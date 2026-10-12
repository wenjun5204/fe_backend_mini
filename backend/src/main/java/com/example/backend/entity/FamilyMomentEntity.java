package com.example.backend.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import java.time.LocalDateTime;

/**
 * 家人瞬间(照片+一句话,家族时间线):id IDENTITY 单调即 createdAt 倒序游标。
 * familyId 为发布时快照(与 day_record 同构);userId=发布人=唯一可删人(隐私权红线,角色不豁免)。
 */
@Entity
@Table(name = "family_moment", indexes = @Index(name = "idx_moment_family", columnList = "family_id"))
public class FamilyMomentEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 发布人当时所属家族快照(瞬间全线要求家族,不会为 NULL) */
    @Column(name = "family_id", nullable = false)
    private Long familyId;

    /** 发布人=唯一可删人(仅本人可删,户主/家人均 40400 不泄露存在性) */
    @Column(name = "user_id", nullable = false)
    private Long userId;

    /** 封面照片 id(scene=MOMENT,创建即绑定 ORPHAN→ACTIVE;软删联动照片置 DELETED) */
    @Column(name = "photo_id", nullable = false, length = 20)
    private String photoId;

    /** 一句话正文 1-60 字(过违禁词表) */
    @Column(nullable = false, length = 60)
    private String text;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 8)
    private MomentStatus status;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    /** 瞬间状态:ACTIVE 正常 / DELETED 软删(时间线与素材池即时消失;like/collect 行保留) */
    public enum MomentStatus {
        ACTIVE, DELETED
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getFamilyId() { return familyId; }
    public void setFamilyId(Long familyId) { this.familyId = familyId; }
    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }
    public String getPhotoId() { return photoId; }
    public void setPhotoId(String photoId) { this.photoId = photoId; }
    public String getText() { return text; }
    public void setText(String text) { this.text = text; }
    public MomentStatus getStatus() { return status; }
    public void setStatus(MomentStatus status) { this.status = status; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
