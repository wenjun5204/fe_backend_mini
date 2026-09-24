package com.example.backend.entity;

import com.example.backend.dto.BirthdayDtos.CalendarType;
import com.example.backend.dto.BirthdayDtos.SubjectType;
import com.example.backend.dto.BirthdayDtos.Visibility;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** 生日簿持久化记录；日期换算结果按请求实时计算，避免跨年缓存失效。 */
@Entity
@Table(name = "birthday_record")
public class BirthdayRecordEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long familyId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 8)
    private SubjectType subjectType;

    @Column
    private Long subjectUserId;

    @Column(nullable = false, length = 12)
    private String displayName;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 8)
    private CalendarType calendarType;

    @Column(name = "birthday_month", nullable = false)
    private int month;

    @Column(name = "birthday_day", nullable = false)
    private int day;

    @Column(nullable = false)
    private boolean isLeapMonth;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 8)
    private Visibility visibility;

    @Column(nullable = false)
    private Long createdBy;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getFamilyId() { return familyId; }
    public void setFamilyId(Long familyId) { this.familyId = familyId; }
    public SubjectType getSubjectType() { return subjectType; }
    public void setSubjectType(SubjectType subjectType) { this.subjectType = subjectType; }
    public Long getSubjectUserId() { return subjectUserId; }
    public void setSubjectUserId(Long subjectUserId) { this.subjectUserId = subjectUserId; }
    public String getDisplayName() { return displayName; }
    public void setDisplayName(String displayName) { this.displayName = displayName; }
    public CalendarType getCalendarType() { return calendarType; }
    public void setCalendarType(CalendarType calendarType) { this.calendarType = calendarType; }
    public int getMonth() { return month; }
    public void setMonth(int month) { this.month = month; }
    public int getDay() { return day; }
    public void setDay(int day) { this.day = day; }
    public boolean isLeapMonth() { return isLeapMonth; }
    public void setLeapMonth(boolean leapMonth) { isLeapMonth = leapMonth; }
    public Visibility getVisibility() { return visibility; }
    public void setVisibility(Visibility visibility) { this.visibility = visibility; }
    public Long getCreatedBy() { return createdBy; }
    public void setCreatedBy(Long createdBy) { this.createdBy = createdBy; }
}
