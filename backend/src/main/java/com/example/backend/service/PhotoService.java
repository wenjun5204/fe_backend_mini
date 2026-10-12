package com.example.backend.service;

import com.example.backend.common.ApiException;
import com.example.backend.common.CurrentUser;
import com.example.backend.dto.PhotoDtos.PhotoMimeType;
import com.example.backend.dto.PhotoDtos.PhotoScene;
import com.example.backend.dto.PhotoDtos.PhotoUploadRequest;
import com.example.backend.dto.PhotoDtos.PhotoUploadResponse;
import com.example.backend.entity.PhotoAssetEntity;
import com.example.backend.entity.PhotoAssetEntity.PhotoStatus;
import com.example.backend.repository.PhotoAssetRepository;
import java.security.SecureRandom;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Base64;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 照片管道:base64 JSON 上传(校验序 40000 优先 40300,先廉价的先做)与免鉴权回图。
 * 回图为能力 URL:photoId 18 位 base62 高熵令牌,不存在/已删统一 40400 同文案(不泄露存在性)。
 * 大载荷 DoS 防线:解码前字符串长度硬闸,不解码超大 base64。
 */
@Service
public class PhotoService {

    private static final String BASE62 = "0123456789abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ";
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final int PHOTO_ID_LENGTH = 18;
    /** 解码前硬闸:512000×4/3≈682,668 + Base64 padding 余量(防 base64 解码放大 DoS) */
    private static final int MAX_BASE64_CHARS = 700_000;
    private static final int MAX_BYTES = 512_000;
    private static final int DAILY_LIMIT = 20;
    private static final String PHOTO_ID_PATTERN = "[0-9a-zA-Z]{16,20}";
    private static final String NOT_FOUND_MESSAGE = "没有找到这张照片";

    private final PhotoAssetRepository photoAssetRepository;

    public PhotoService(PhotoAssetRepository photoAssetRepository) {
        this.photoAssetRepository = photoAssetRepository;
    }

    @Transactional
    public PhotoUploadResponse upload(PhotoUploadRequest request) {
        Long userId = CurrentUser.get();
        // 1. scene 枚举校验(MOMENT/CAPSULE)
        PhotoScene scene = request == null || request.getScene() == null
                ? null : PhotoScene.fromValue(request.getScene().trim());
        if (scene == null) {
            throw ApiException.param("照片用途不对，请重新选择");
        }
        // 2. 解前先验长度:空/超长都不解码
        String imageBase64 = request.getImageBase64();
        if (imageBase64 == null || imageBase64.isBlank()) {
            throw ApiException.param("请选择一张照片");
        }
        if (imageBase64.length() > MAX_BASE64_CHARS) {
            throw ApiException.param("照片太大，请压缩后再传");
        }
        // 3. 解码与解码后大小
        byte[] data;
        try {
            data = Base64.getDecoder().decode(imageBase64);
        } catch (IllegalArgumentException e) {
            throw ApiException.param("照片数据损坏，请重试");
        }
        if (data.length < 1 || data.length > MAX_BYTES) {
            throw ApiException.param("照片太大，请压缩后再传");
        }
        // 4. mime:始终嗅探字节头;请求带 mimeType 须属白名单且与嗅探一致(防 Content-Type 撒谎)
        PhotoMimeType sniffed = sniff(data);
        if (sniffed == null) {
            throw ApiException.param("只支持 jpg、png、webp 照片");
        }
        String declared = request.getMimeType();
        if (declared != null && !declared.isBlank()) {
            PhotoMimeType declaredType = PhotoMimeType.fromMime(declared.trim());
            if (declaredType == null || declaredType != sniffed) {
                throw ApiException.param("只支持 jpg、png、webp 照片");
            }
        }
        // 5. 频控:每日每人 20 张(实体表当日行数即额度账本)
        LocalDateTime todayStart = LocalDate.now().atStartOfDay();
        if (photoAssetRepository.countByUserIdAndCreatedAtGreaterThanEqual(userId, todayStart) >= DAILY_LIMIT) {
            throw ApiException.rateLimited("今天传了 20 张照片，明天再来");
        }
        // 6. photoId 签发:SecureRandom base62,唯一索引兜底撞码重生成(每次新 id,不复用)
        PhotoAssetEntity entity = new PhotoAssetEntity();
        entity.setUserId(userId);
        entity.setScene(scene);
        entity.setMimeType(sniffed.getMime());
        entity.setSizeBytes(data.length);
        entity.setData(data);
        entity.setStatus(PhotoStatus.ORPHAN);
        entity.setCreatedAt(LocalDateTime.now());
        for (int attempt = 0; attempt < 5; attempt++) {
            String candidate = randomPhotoId();
            if (photoAssetRepository.existsByPhotoId(candidate)) {
                continue;
            }
            entity.setPhotoId(candidate);
            try {
                photoAssetRepository.saveAndFlush(entity);
                break;
            } catch (org.springframework.dao.DataIntegrityViolationException e) {
                entity.setPhotoId(null);
            }
        }
        if (entity.getPhotoId() == null) {
            throw new IllegalStateException("照片标识签发失败，请稍后再试");
        }
        PhotoUploadResponse response = new PhotoUploadResponse();
        response.setPhotoId(entity.getPhotoId());
        response.setPhotoUrl("/api/photos/" + entity.getPhotoId());
        response.setSizeBytes(entity.getSizeBytes());
        return response;
    }

    /** 回图(免鉴权):photoId 内容不可变,可放心长缓存;ORPHAN 与 ACTIVE 均回图(发布前预览) */
    @Transactional(readOnly = true)
    public ResponseEntity<byte[]> getPhoto(String photoId) {
        if (photoId == null || !photoId.matches(PHOTO_ID_PATTERN)) {
            throw ApiException.notFound(NOT_FOUND_MESSAGE);
        }
        PhotoAssetEntity entity = photoAssetRepository.findByPhotoId(photoId).orElse(null);
        if (entity == null || entity.getStatus() == PhotoStatus.DELETED) {
            throw ApiException.notFound(NOT_FOUND_MESSAGE);
        }
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_TYPE, entity.getMimeType())
                .header(HttpHeaders.CACHE_CONTROL,
                        CacheControl.maxAge(java.time.Duration.ofDays(365)).cachePublic().getHeaderValue() + ", immutable")
                .header("X-Content-Type-Options", "nosniff")
                .body(entity.getData());
    }

    /** ORPHAN GC:48h 未绑定照片物理删行(重复执行零行,幂等;删行后回图自然 40400);返回扫除行数 */
    @Scheduled(fixedDelay = 3600_000)
    @Transactional
    public long gcOrphanPhotos() {
        return photoAssetRepository.deleteByStatusAndCreatedAtBefore(PhotoStatus.ORPHAN,
                LocalDateTime.now().minusHours(48));
    }

    /** 字节头嗅探:FF D8 FF=jpeg、89 50 4E 47=png、RIFF...WEBP(offset 8)=webp;嗅不出返回 null */
    private PhotoMimeType sniff(byte[] data) {
        if (data.length >= 3 && (data[0] & 0xFF) == 0xFF && (data[1] & 0xFF) == 0xD8 && (data[2] & 0xFF) == 0xFF) {
            return PhotoMimeType.JPEG;
        }
        if (data.length >= 4 && (data[0] & 0xFF) == 0x89 && data[1] == 'P' && data[2] == 'N' && data[3] == 'G') {
            return PhotoMimeType.PNG;
        }
        if (data.length >= 12 && data[0] == 'R' && data[1] == 'I' && data[2] == 'F' && data[3] == 'F'
                && data[8] == 'W' && data[9] == 'E' && data[10] == 'B' && data[11] == 'P') {
            return PhotoMimeType.WEBP;
        }
        return null;
    }

    private String randomPhotoId() {
        StringBuilder builder = new StringBuilder(PHOTO_ID_LENGTH);
        for (int i = 0; i < PHOTO_ID_LENGTH; i++) {
            builder.append(BASE62.charAt(RANDOM.nextInt(BASE62.length())));
        }
        return builder.toString();
    }
}
