package com.example.backend.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.LocalDateTime;

/**
 * 值班提醒配置:用户级一条(uk(user_id))。
 * timing 是提醒时机意愿;订阅授权额度走既有 NotifyService 账本(POST /api/notify/subscribe),
 * 两层解耦——本表只存「用户想要什么」,授权表存「实际能发多少」。
 * 真实 subscribeMessage.send 属部署联调阶段,本实体不触碰发送。
 */
@Entity
@Table(name = "duty_remind",
        uniqueConstraints = @UniqueConstraint(name = "uk_duty_remind", columnNames = {"userId"}))
public class DutyRemindEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long userId;

    /** 提醒时机:advance_1d(提前1天12:00,默认)|same_day_08(当天08:00)|advance_2h(提前2小时) */
    @Column(nullable = false, length = 16)
    private String timing;

    /** 提醒意愿开关;关闭后状态卡显示「提醒未开启」弱引导 */
    @Column(nullable = false)
    private boolean enabled;

    @Column(nullable = false)
    private LocalDateTime updatedAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }
    public String getTiming() { return timing; }
    public void setTiming(String timing) { this.timing = timing; }
    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
