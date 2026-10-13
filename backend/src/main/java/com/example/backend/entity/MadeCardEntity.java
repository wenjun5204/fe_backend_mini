package com.example.backend.entity;

import com.example.backend.dto.CardDtos.CardKind;
import com.example.backend.dto.CardDtos.CardStatus;
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

/** 卡片定稿存档;payload 为按 kind 异构的定稿内容 JSON 字符串;totalHearts 不落库(访客端实时聚合)。 */
@Entity
@Table(name = "made_card", indexes = @Index(name = "idx_card_user", columnList = "user_id"))
public class MadeCardEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long userId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private CardKind templateKind;

    /** @Lob String:H2→CLOB、MySQL→LONGTEXT,双 profile 兼容,不用 MySQL 方言 JSON 列 */
    @Lob
    @Column(nullable = false)
    private String payload;

    /** 仅 DAYS_CARD:关联 DayRecord(kind=TOGETHER);独立输入起始日为 NULL */
    @Column
    private Long sourceDayId;

    /** 仅 CAPSULE:引用 TimeCapsule(引用非包含) */
    @Column
    private Long capsuleId;

    /** 首次分享签发的 8 位 base62 场景码,全库唯一;NULL=未分享;软删后保留(访客端按状态过滤) */
    @Column(name = "share_scene", unique = true, length = 12)
    private String shareScene;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 8)
    private CardStatus status;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }
    public CardKind getTemplateKind() { return templateKind; }
    public void setTemplateKind(CardKind templateKind) { this.templateKind = templateKind; }
    public String getPayload() { return payload; }
    public void setPayload(String payload) { this.payload = payload; }
    public Long getSourceDayId() { return sourceDayId; }
    public void setSourceDayId(Long sourceDayId) { this.sourceDayId = sourceDayId; }
    public Long getCapsuleId() { return capsuleId; }
    public void setCapsuleId(Long capsuleId) { this.capsuleId = capsuleId; }
    public String getShareScene() { return shareScene; }
    public void setShareScene(String shareScene) { this.shareScene = shareScene; }
    public CardStatus getStatus() { return status; }
    public void setStatus(CardStatus status) { this.status = status; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
