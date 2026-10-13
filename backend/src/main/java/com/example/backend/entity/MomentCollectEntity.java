package com.example.backend.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.LocalDateTime;

/**
 * 瞬间收藏进日子(生日福卡素材池):uk(moment_id,day_id,user_id) 幂等;
 * 同一瞬间可被不同家人收藏到同一/不同日子(共享素材语义)。
 * dayId 语义=生日福卡素材池(契约限定 kind=BIRTHDAY);瞬间软删后行保留但素材池按 moment ACTIVE 过滤。
 */
@Entity
@Table(name = "moment_collect",
        uniqueConstraints = @UniqueConstraint(name = "uk_moment_collect",
                columnNames = {"moment_id", "day_id", "user_id"}),
        indexes = @Index(name = "idx_collect_day", columnList = "day_id"))
public class MomentCollectEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "moment_id", nullable = false)
    private Long momentId;

    /** 收藏目标 DayRecord(须 kind=BIRTHDAY 且收藏人可见) */
    @Column(name = "day_id", nullable = false)
    private Long dayId;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    /** 收藏时间;素材池排序键(id 单调≈collectedAt 倒序) */
    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getMomentId() { return momentId; }
    public void setMomentId(Long momentId) { this.momentId = momentId; }
    public Long getDayId() { return dayId; }
    public void setDayId(Long dayId) { this.dayId = dayId; }
    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
