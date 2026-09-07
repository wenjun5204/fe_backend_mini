package com.example.backend.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.LocalDate;

/** 抽签记录:user+date 唯一,抽签/分享/任务各一次性 */
@Entity
@Table(name = "fortune_draw_record", uniqueConstraints = {
        @UniqueConstraint(name = "uk_draw_user_date", columnNames = {"userId", "date"})
})
public class FortuneDrawRecordEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long userId;

    @Column(nullable = false)
    private Long familyId;

    @Column(nullable = false)
    private LocalDate date;

    @Column(nullable = false)
    private boolean shared = false;

    @Column(nullable = false)
    private boolean taskDone = false;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }
    public Long getFamilyId() { return familyId; }
    public void setFamilyId(Long familyId) { this.familyId = familyId; }
    public LocalDate getDate() { return date; }
    public void setDate(LocalDate date) { this.date = date; }
    public boolean isShared() { return shared; }
    public void setShared(boolean shared) { this.shared = shared; }
    public boolean isTaskDone() { return taskDone; }
    public void setTaskDone(boolean taskDone) { this.taskDone = taskDone; }
}
