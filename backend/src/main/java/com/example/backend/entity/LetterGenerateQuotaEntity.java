package com.example.backend.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.LocalDate;

/** 感恩信生成每日频控账本(userId+日期 upsert);AI 与降级路径统一计数,云托管水平扩/重启不丢额度。 */
@Entity
@Table(name = "letter_generate_quota", uniqueConstraints =
        @UniqueConstraint(name = "uk_letter_quota", columnNames = {"user_id", "quota_date"}))
public class LetterGenerateQuotaEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "quota_date", nullable = false)
    private LocalDate quotaDate;

    @Column(nullable = false)
    private int count;

    public LetterGenerateQuotaEntity() {
    }

    public LetterGenerateQuotaEntity(Long userId, LocalDate quotaDate, int count) {
        this.userId = userId;
        this.quotaDate = quotaDate;
        this.count = count;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }
    public LocalDate getQuotaDate() { return quotaDate; }
    public void setQuotaDate(LocalDate quotaDate) { this.quotaDate = quotaDate; }
    public int getCount() { return count; }
    public void setCount(int count) { this.count = count; }
}
