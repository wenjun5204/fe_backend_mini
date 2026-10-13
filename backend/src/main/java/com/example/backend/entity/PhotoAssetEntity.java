package com.example.backend.entity;

import com.example.backend.dto.PhotoDtos.PhotoScene;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;
import java.time.LocalDateTime;

/**
 * 照片资产(能力 URL 模式):photoId 即回图令牌,16-20 位 base62 高熵随机串,内容不可变。
 * 生命周期:ORPHAN(上传未绑定)→ ACTIVE(被瞬间/胶囊绑定)→ DELETED(随宿主删除,回图 40400);
 * ORPHAN 超 48h 由定时 GC 物理删行。
 */
@Entity
@Table(name = "photo_asset", indexes = @Index(name = "idx_photo_user", columnList = "user_id"))
public class PhotoAssetEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** capability URL 令牌(实现固定 18 位 base62,62^18≈1.8×10^32 不可枚举);全库唯一 */
    @Column(name = "photo_id", nullable = false, unique = true, length = 20)
    private String photoId;

    /** 上传人(归属校验 + 日限计数) */
    @Column(nullable = false)
    private Long userId;

    /** 照片用途声明:绑定期校验一致性(MOMENT/CAPSULE) */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 8)
    private PhotoScene scene;

    /** image/jpeg、image/png、image/webp(上传时字节头嗅探交叉校验,回图 Content-Type 同源) */
    @Column(nullable = false, length = 24)
    private String mimeType;

    /** 解码后字节数(≤512000,应用层硬闸) */
    @Column(nullable = false)
    private int sizeBytes;

    /** @Lob byte[]:H2→blob、MySQL→longblob(Hibernate 7 原生 byte[] 物化,双 profile 兼容) */
    @Lob
    @Column(nullable = false)
    private byte[] data;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 8)
    private PhotoStatus status;

    /** 绑定引用:家人瞬间(绑定后回写,本批仅建列) */
    @Column
    private Long momentId;

    /** 绑定引用:时光胶囊(绑定后回写,本批仅建列) */
    @Column
    private Long capsuleId;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    /** 照片状态:ORPHAN 未绑定(GC 基准)/ ACTIVE 已绑定 / DELETED 已删(回图 40400) */
    public enum PhotoStatus {
        ORPHAN, ACTIVE, DELETED
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getPhotoId() { return photoId; }
    public void setPhotoId(String photoId) { this.photoId = photoId; }
    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }
    public PhotoScene getScene() { return scene; }
    public void setScene(PhotoScene scene) { this.scene = scene; }
    public String getMimeType() { return mimeType; }
    public void setMimeType(String mimeType) { this.mimeType = mimeType; }
    public int getSizeBytes() { return sizeBytes; }
    public void setSizeBytes(int sizeBytes) { this.sizeBytes = sizeBytes; }
    public byte[] getData() { return data; }
    public void setData(byte[] data) { this.data = data; }
    public PhotoStatus getStatus() { return status; }
    public void setStatus(PhotoStatus status) { this.status = status; }
    public Long getMomentId() { return momentId; }
    public void setMomentId(Long momentId) { this.momentId = momentId; }
    public Long getCapsuleId() { return capsuleId; }
    public void setCapsuleId(Long capsuleId) { this.capsuleId = capsuleId; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
