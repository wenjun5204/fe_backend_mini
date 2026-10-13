package com.example.backend.entity;

import com.example.backend.dto.CapsuleDtos.CapsuleOpenRule;
import com.example.backend.dto.CapsuleDtos.CapsuleStatus;
import com.example.backend.dto.CapsuleDtos.CapsuleType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import java.time.LocalDate;
import java.time.LocalDateTime;

/** 时光胶囊;openDate 为唯一权威开箱判定(openRule 仅展示);contentText 明文存、读取由接口门控。 */
@Entity
@Table(name = "time_capsule", indexes = @Index(name = "idx_capsule_user", columnList = "user_id"))
public class TimeCapsuleEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long userId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 8)
    private CapsuleType type;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 24)
    private CapsuleOpenRule openRule;

    @Column(name = "open_date", nullable = false)
    private LocalDate openDate;

    @Column(nullable = false, length = 40)
    private String coverHint;

    @Column(nullable = false, length = 500)
    private String contentText;

    @Column(length = 255)
    private String photoUrl;

    /** 关联照片 id(v1.8 新增,封存时绑定,优先于 photoUrl 直传旧形态;门控同 contentText) */
    @Column(name = "photo_id", length = 20)
    private String photoId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 8)
    private CapsuleStatus status;

    @Column(nullable = false)
    private LocalDateTime sealedAt;

    /** 首次开箱时间;SEALED 为 NULL */
    @Column
    private LocalDateTime openedAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }
    public CapsuleType getType() { return type; }
    public void setType(CapsuleType type) { this.type = type; }
    public CapsuleOpenRule getOpenRule() { return openRule; }
    public void setOpenRule(CapsuleOpenRule openRule) { this.openRule = openRule; }
    public LocalDate getOpenDate() { return openDate; }
    public void setOpenDate(LocalDate openDate) { this.openDate = openDate; }
    public String getCoverHint() { return coverHint; }
    public void setCoverHint(String coverHint) { this.coverHint = coverHint; }
    public String getContentText() { return contentText; }
    public void setContentText(String contentText) { this.contentText = contentText; }
    public String getPhotoUrl() { return photoUrl; }
    public void setPhotoUrl(String photoUrl) { this.photoUrl = photoUrl; }
    public String getPhotoId() { return photoId; }
    public void setPhotoId(String photoId) { this.photoId = photoId; }
    public CapsuleStatus getStatus() { return status; }
    public void setStatus(CapsuleStatus status) { this.status = status; }
    public LocalDateTime getSealedAt() { return sealedAt; }
    public void setSealedAt(LocalDateTime sealedAt) { this.sealedAt = sealedAt; }
    public LocalDateTime getOpenedAt() { return openedAt; }
    public void setOpenedAt(LocalDateTime openedAt) { this.openedAt = openedAt; }
}
