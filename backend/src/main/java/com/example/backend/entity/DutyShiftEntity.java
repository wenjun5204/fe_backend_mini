package com.example.backend.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 值班排班记录:用户 × 业务日期一条。
 * 唯一约束 uk(user_id, duty_date) 是「单日一条」的物理保证:单日编辑 upsert,批量排班覆盖写入。
 * shift 存字符串 + 服务层 DutyService 枚举校验(DB 无 ENUM,规避 H2/MySQL 枚举列扩值迁移坑)。
 */
@Entity
@Table(name = "duty_shift",
        uniqueConstraints = @UniqueConstraint(name = "uk_duty_shift", columnNames = {"userId", "dutyDate"}))
public class DutyShiftEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long userId;

    /** 业务日期(月历格子/下次值班扫描均按此列) */
    @Column(nullable = false)
    private LocalDate dutyDate;

    /** 班次:early|mid|night|rest(服务层校验) */
    @Column(nullable = false, length = 16)
    private String shift;

    /** 来源:manual(单日编辑)或 rule:daily/on1off1/on2off2/emnr/weekday(批量排班);MVP 仅存档 */
    @Column(nullable = false, length = 32)
    private String source;

    @Column(nullable = false)
    private LocalDateTime updatedAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }
    public LocalDate getDutyDate() { return dutyDate; }
    public void setDutyDate(LocalDate dutyDate) { this.dutyDate = dutyDate; }
    public String getShift() { return shift; }
    public void setShift(String shift) { this.shift = shift; }
    public String getSource() { return source; }
    public void setSource(String source) { this.source = source; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
