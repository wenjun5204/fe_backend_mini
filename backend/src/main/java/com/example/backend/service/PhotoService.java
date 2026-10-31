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
import java.io.IOException;
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
import org.springframework.web.multipart.MultipartFile;

/**
 * 照片管道:双形态上传(v1.9.2——multipart 主形态 + base64 JSON 兼容形态,校验序与错误码完全一致)
 * 与免鉴权回图。回图为能力 URL:photoId 18 位 base62 高熵令牌,不存在/已删统一 40400 同文案
 * (不泄露存在性)。大载荷 DoS 防线:multipart 先验 getSize 再取字节;JSON 解码前字符串长度硬闸,
 * 不解码超大 base64。入库不二次压缩——前端作主压缩 ≤200KB(Java ImageIO 不支持 webp,服务端压缩对
 * 三分之一合法 mime 失效,且压缩责任前置可省用户上行流量),服务端 512000 字节硬闸兜底。
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
        // 3. 解码(解码后大小校验移入 store 共用核心)
        byte[] data;
        try {
            data = Base64.getDecoder().decode(imageBase64);
        } catch (IllegalArgumentException e) {
            throw ApiException.param("照片数据损坏，请重试");
        }
        // 4. mime 交叉校验仅 JSON 形态有(multipart 前端不传 mimeType,嗅探即权威)
        String declared = request.getMimeType();
        if (declared != null && !declared.isBlank()) {
            PhotoMimeType declaredType = PhotoMimeType.fromMime(declared.trim());
            if (declaredType == null || declaredType != sniff(data)) {
                throw ApiException.param("只支持 jpg、png、webp 照片");
            }
        }
        return store(userId, data, scene);
    }

    /** multipart 主形态(v1.9.2):先验 getSize 再取字节(不缓冲超大文件);校验序与 JSON 形态一致 */
    @Transactional
    public PhotoUploadResponse upload(MultipartFile image, String scene) {
        Long userId = CurrentUser.get();
        // 1. scene 枚举校验(与 JSON 形态同文案同顺序)
        PhotoScene parsed = scene == null ? null : PhotoScene.fromValue(scene.trim());
        if (parsed == null) {
            throw ApiException.param("照片用途不对，请重新选择");
        }
        // 2. 文件缺失/空(缺 part 或空文件,语义对齐 JSON 形态的「请选择一张照片」)
        if (image == null || image.isEmpty()) {
            throw ApiException.param("请选择一张照片");
        }
        // 3. 解码前等价硬闸:>1MB 在 multipart 解析层已被网关挡回(40000);
        //    (1MB, 512000] 到控制器,此处统一 40000 文案
        if (image.getSize() > MAX_BYTES) {
            throw ApiException.param("照片太大，请压缩后再传");
        }
        byte[] data;
        try {
            data = image.getBytes();
        } catch (IOException e) {
            throw ApiException.param("照片数据损坏，请重试");
        }
        return store(userId, data, parsed);
    }

    /** 双形态共用落库核心:大小/嗅探/频控/签发(校验序 40000 优先 40300,先廉价的先做) */
    private PhotoUploadResponse store(Long userId, byte[] data, PhotoScene scene) {
        if (data.length < 1 || data.length > MAX_BYTES) {
            throw ApiException.param("照片太大，请压缩后再传");
        }
        PhotoMimeType sniffed = sniff(data);
        if (sniffed == null) {
            throw ApiException.param("只支持 jpg、png、webp 照片");
        }
        // 频控:每日每人 20 张(实体表当日行数即额度账本)
        LocalDateTime todayStart = LocalDate.now().atStartOfDay();
        if (photoAssetRepository.countByUserIdAndCreatedAtGreaterThanEqual(userId, todayStart) >= DAILY_LIMIT) {
            throw ApiException.rateLimited("今天传了 20 张照片，明天再来");
        }
        // photoId 签发:SecureRandom base62,唯一索引兜底撞码重生成(每次新 id,不复用)
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
