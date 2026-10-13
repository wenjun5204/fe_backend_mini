package com.example.backend.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;

/** 福值事件:福值变更唯一路径,只增不减,可审计 */
@Entity
@Table(name = "bless_event")
public class BlessEventEntity {

    public enum Type {
        /** 抽签 +5 */ DRAW_CARD(5),
        /** 分享 +10 */ SHARE_CARD(10),
        /** 完成宜事项 +15 */ TASK_DONE(15),
        /** 添福 +8 */ TIAN_FU(8),
        /** 邀请家人 +30 */ INVITE_JOIN(30),
        /** 全家连续7天 +50 */ FAMILY_STREAK_7D(50);

        private final int amount;

        Type(int amount) { this.amount = amount; }

        public int amount() { return amount; }
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long userId;

    @Column(nullable = false)
    private Long familyId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Type type;

    @Column(nullable = false)
    private int amount;

    /** 互动目标(添福时为被添福人),其余为空 */
    @Column
    private Long targetUserId;

    @Column(nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }
    public Long getFamilyId() { return familyId; }
    public void setFamilyId(Long familyId) { this.familyId = familyId; }
    public Type getType() { return type; }
    public void setType(Type type) { this.type = type; }
    public int getAmount() { return amount; }
    public void setAmount(int amount) { this.amount = amount; }
    public Long getTargetUserId() { return targetUserId; }
    public void setTargetUserId(Long targetUserId) { this.targetUserId = targetUserId; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
