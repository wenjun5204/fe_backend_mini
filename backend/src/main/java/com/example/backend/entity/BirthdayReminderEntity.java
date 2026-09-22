package com.example.backend.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

/** 当前用户对某条生日记录的提醒偏好；不保存订阅消息授权信息。 */
@Entity
@Table(name = "birthday_reminder", uniqueConstraints = {
        @UniqueConstraint(name = "uk_birthday_reminder", columnNames = {"birthdayId", "userId"})
})
public class BirthdayReminderEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long birthdayId;

    @Column(nullable = false)
    private Long userId;

    @Column(nullable = false, length = 16)
    private String reminderDays = "";

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getBirthdayId() { return birthdayId; }
    public void setBirthdayId(Long birthdayId) { this.birthdayId = birthdayId; }
    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }
    public String getReminderDays() { return reminderDays; }
    public void setReminderDays(String reminderDays) { this.reminderDays = reminderDays; }
}
