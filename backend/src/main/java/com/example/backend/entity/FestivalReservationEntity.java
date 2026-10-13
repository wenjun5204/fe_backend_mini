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
 * 节日/上线预约登记:「上线提醒我」(live=false 模板)与节日提醒(festivalDate 模板)共用。
 * 唯一约束 uk(user_id, template_key) 是幂等的物理保证:重复预约返回既有行,createdAt=首次登记时间不变。
 */
@Entity
@Table(name = "festival_reservation",
        uniqueConstraints = @UniqueConstraint(name = "uk_festival_reservation", columnNames = {"userId", "templateKey"}))
public class FestivalReservationEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long userId;

    /** 模板键(TemplateKey 枚举名) */
    @Column(nullable = false, length = 32)
    private String templateKey;

    /** 首次登记时间(重复预约不变);清单按此降序 */
    @Column(nullable = false)
    private LocalDateTime createdAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }
    public String getTemplateKey() { return templateKey; }
    public void setTemplateKey(String templateKey) { this.templateKey = templateKey; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
