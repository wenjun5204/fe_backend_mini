package com.example.backend.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

/** 当前用户对某条日子的提醒偏好(每位家人独立配置);reminders 为 CSV,空串=关闭。 */
@Entity
@Table(name = "day_reminder", uniqueConstraints = {
        @UniqueConstraint(name = "uk_day_reminder", columnNames = {"dayId", "userId"})
})
public class DayReminderEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long dayId;

    @Column(nullable = false)
    private Long userId;

    @Column(nullable = false, length = 16)
    private String reminders = "";

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getDayId() { return dayId; }
    public void setDayId(Long dayId) { this.dayId = dayId; }
    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }
    public String getReminders() { return reminders; }
    public void setReminders(String reminders) { this.reminders = reminders; }
}
