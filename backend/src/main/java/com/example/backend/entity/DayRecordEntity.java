package com.example.backend.entity;

import com.example.backend.dto.BirthdayDtos.CalendarType;
import com.example.backend.dto.DayDtos.DayKind;
import com.example.backend.dto.DayDtos.DayVisibility;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;

/** 日子管家持久化记录;落点/相伴天数实时计算不落库。TOGETHER 起始年取 createdAt,月日冗余存起始月日。 */
@Entity
@Table(name = "day_record")
public class DayRecordEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long userId;

    /** 创建人当时所属家族快照;无家族用户为 NULL(每人限 1 家族,快照即当前值) */
    @Column
    private Long familyId;

    @Column(nullable = false, length = 12)
    private String relation;

    @Column(nullable = false, length = 8)
    private String emoji;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private DayKind kind;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 8)
    private CalendarType calendarType;

    @Column(name = "day_month", nullable = false)
    private int month;

    @Column(name = "day_day", nullable = false)
    private int day;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 8)
    private DayVisibility visibility;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }
    public Long getFamilyId() { return familyId; }
    public void setFamilyId(Long familyId) { this.familyId = familyId; }
    public String getRelation() { return relation; }
    public void setRelation(String relation) { this.relation = relation; }
    public String getEmoji() { return emoji; }
    public void setEmoji(String emoji) { this.emoji = emoji; }
    public DayKind getKind() { return kind; }
    public void setKind(DayKind kind) { this.kind = kind; }
    public CalendarType getCalendarType() { return calendarType; }
    public void setCalendarType(CalendarType calendarType) { this.calendarType = calendarType; }
    public int getMonth() { return month; }
    public void setMonth(int month) { this.month = month; }
    public int getDay() { return day; }
    public void setDay(int day) { this.day = day; }
    public DayVisibility getVisibility() { return visibility; }
    public void setVisibility(DayVisibility visibility) { this.visibility = visibility; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
