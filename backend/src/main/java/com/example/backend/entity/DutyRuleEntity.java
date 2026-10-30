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
 * 值班周期规则:用户级单条生效(uk(user_id)),重新应用即整行覆盖。
 * 删除单日班次仅删 duty_shift 行,不触碰本行——重新应用规则会回填(PRD §7)。
 */
@Entity
@Table(name = "duty_rule",
        uniqueConstraints = @UniqueConstraint(name = "uk_duty_rule", columnNames = {"userId"}))
public class DutyRuleEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long userId;

    /** 规则类型:daily|on1off1|on2off2|emnr|weekday(服务层校验) */
    @Column(nullable = false, length = 16)
    private String ruleType;

    @Column(nullable = false)
    private LocalDate startDate;

    /** 实际应用截止日(跨度>365 天截断后的值) */
    @Column(nullable = false)
    private LocalDate endDate;

    /** 有规则恒 true;无规则=无行(查询返回空壳) */
    @Column(nullable = false)
    private boolean active;

    /** 最近一次应用填充天数存档(weekday 类只数命中日) */
    @Column(nullable = false)
    private int appliedDays;

    @Column(nullable = false)
    private LocalDateTime updatedAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }
    public String getRuleType() { return ruleType; }
    public void setRuleType(String ruleType) { this.ruleType = ruleType; }
    public LocalDate getStartDate() { return startDate; }
    public void setStartDate(LocalDate startDate) { this.startDate = startDate; }
    public LocalDate getEndDate() { return endDate; }
    public void setEndDate(LocalDate endDate) { this.endDate = endDate; }
    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }
    public int getAppliedDays() { return appliedDays; }
    public void setAppliedDays(int appliedDays) { this.appliedDays = appliedDays; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
