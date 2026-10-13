package com.example.backend.dto;

import com.fasterxml.jackson.annotation.JsonCreator;
import java.util.List;

/**
 * 日子管家契约模型;落点/相伴天数为实时计算字段,TOGETHER 不参与落点换算。
 * CalendarType 复用 BirthdayDtos(契约中为同一 schema)。
 */
public final class DayDtos {

    private DayDtos() {
    }

    /** 非法枚举值按 null 落地,由 service 层统一回 40000(契约:kind/visibility 非法属参数错误) */
    public enum DayKind {
        BIRTHDAY, ANNIVERSARY, TOGETHER;

        @JsonCreator
        public static DayKind fromValue(String value) {
            for (DayKind kind : values()) {
                if (kind.name().equals(value)) {
                    return kind;
                }
            }
            return null;
        }
    }

    public enum DayVisibility {
        FAMILY, PRIVATE;

        @JsonCreator
        public static DayVisibility fromValue(String value) {
            for (DayVisibility visibility : values()) {
                if (visibility.name().equals(value)) {
                    return visibility;
                }
            }
            return null;
        }
    }

    public static class DayUpsertRequest {
        private String relation;
        private String emoji;
        private DayKind kind;
        private BirthdayDtos.CalendarType calendarType;
        private Integer month;
        private Integer day;
        private DayVisibility visibility;
        private List<Integer> reminders;
        private Integer year;

        public String getRelation() { return relation; }
        public void setRelation(String relation) { this.relation = relation; }
        public String getEmoji() { return emoji; }
        public void setEmoji(String emoji) { this.emoji = emoji; }
        public DayKind getKind() { return kind; }
        public void setKind(DayKind kind) { this.kind = kind; }
        public BirthdayDtos.CalendarType getCalendarType() { return calendarType; }
        public void setCalendarType(BirthdayDtos.CalendarType calendarType) { this.calendarType = calendarType; }
        public Integer getMonth() { return month; }
        public void setMonth(Integer month) { this.month = month; }
        public Integer getDay() { return day; }
        public void setDay(Integer day) { this.day = day; }
        public Integer getYear() { return year; }
        public void setYear(Integer year) { this.year = year; }
        public DayVisibility getVisibility() { return visibility; }
        public void setVisibility(DayVisibility visibility) { this.visibility = visibility; }
        public List<Integer> getReminders() { return reminders; }
        public void setReminders(List<Integer> reminders) { this.reminders = reminders; }
    }

    public static class DayRecord {
        private Long id;
        private Long userId;
        private String relation;
        private String emoji;
        private DayKind kind;
        private BirthdayDtos.CalendarType calendarType;
        private int month;
        private int day;
        private DayVisibility visibility;
        private List<Integer> reminders;
        private String createdAt;
        private String nextDate;
        private String nextDateLunarText;
        private Integer daysUntil;
        private Integer togetherDays;
        private Integer year;
        private Integer anniversaryNumber;
        private String anniversaryTitle;
        private String anniversaryLabel;

        public Long getId() { return id; }
        public void setId(Long id) { this.id = id; }
        public Long getUserId() { return userId; }
        public void setUserId(Long userId) { this.userId = userId; }
        public String getRelation() { return relation; }
        public void setRelation(String relation) { this.relation = relation; }
        public String getEmoji() { return emoji; }
        public void setEmoji(String emoji) { this.emoji = emoji; }
        public DayKind getKind() { return kind; }
        public void setKind(DayKind kind) { this.kind = kind; }
        public BirthdayDtos.CalendarType getCalendarType() { return calendarType; }
        public void setCalendarType(BirthdayDtos.CalendarType calendarType) { this.calendarType = calendarType; }
        public int getMonth() { return month; }
        public void setMonth(int month) { this.month = month; }
        public int getDay() { return day; }
        public void setDay(int day) { this.day = day; }
        public DayVisibility getVisibility() { return visibility; }
        public void setVisibility(DayVisibility visibility) { this.visibility = visibility; }
        public List<Integer> getReminders() { return reminders; }
        public void setReminders(List<Integer> reminders) { this.reminders = reminders; }
        public String getCreatedAt() { return createdAt; }
        public void setCreatedAt(String createdAt) { this.createdAt = createdAt; }
        public String getNextDate() { return nextDate; }
        public void setNextDate(String nextDate) { this.nextDate = nextDate; }
        public String getNextDateLunarText() { return nextDateLunarText; }
        public void setNextDateLunarText(String nextDateLunarText) { this.nextDateLunarText = nextDateLunarText; }
        public Integer getDaysUntil() { return daysUntil; }
        public void setDaysUntil(Integer daysUntil) { this.daysUntil = daysUntil; }
        public Integer getTogetherDays() { return togetherDays; }
        public void setTogetherDays(Integer togetherDays) { this.togetherDays = togetherDays; }
        public Integer getYear() { return year; }
        public void setYear(Integer year) { this.year = year; }
        public Integer getAnniversaryNumber() { return anniversaryNumber; }
        public void setAnniversaryNumber(Integer anniversaryNumber) { this.anniversaryNumber = anniversaryNumber; }
        public String getAnniversaryTitle() { return anniversaryTitle; }
        public void setAnniversaryTitle(String anniversaryTitle) { this.anniversaryTitle = anniversaryTitle; }
        public String getAnniversaryLabel() { return anniversaryLabel; }
        public void setAnniversaryLabel(String anniversaryLabel) { this.anniversaryLabel = anniversaryLabel; }
    }

    public static class DayListResponse {
        private List<DayRecord> items;

        public List<DayRecord> getItems() { return items; }
        public void setItems(List<DayRecord> items) { this.items = items; }
    }

    public static class DayReminderRequest {
        private List<Integer> reminders;

        public List<Integer> getReminders() { return reminders; }
        public void setReminders(List<Integer> reminders) { this.reminders = reminders; }
    }

    public static class DayReminder {
        private Long dayId;
        private List<Integer> reminders;

        public Long getDayId() { return dayId; }
        public void setDayId(Long dayId) { this.dayId = dayId; }
        public List<Integer> getReminders() { return reminders; }
        public void setReminders(List<Integer> reminders) { this.reminders = reminders; }
    }

    public static class DayReminderDueItem {
        private Long dayId;
        private Long userId;
        private int remindOffset;
        private String title;
        private String actionText;
        private DayKind kind;
        private String relation;
        private Integer daysTo;
        private Integer daysSince;

        public Long getDayId() { return dayId; }
        public void setDayId(Long dayId) { this.dayId = dayId; }
        public Long getUserId() { return userId; }
        public void setUserId(Long userId) { this.userId = userId; }
        public int getRemindOffset() { return remindOffset; }
        public void setRemindOffset(int remindOffset) { this.remindOffset = remindOffset; }
        public String getTitle() { return title; }
        public void setTitle(String title) { this.title = title; }
        public String getActionText() { return actionText; }
        public void setActionText(String actionText) { this.actionText = actionText; }
        public DayKind getKind() { return kind; }
        public void setKind(DayKind kind) { this.kind = kind; }
        public String getRelation() { return relation; }
        public void setRelation(String relation) { this.relation = relation; }
        public Integer getDaysTo() { return daysTo; }
        public void setDaysTo(Integer daysTo) { this.daysTo = daysTo; }
        public Integer getDaysSince() { return daysSince; }
        public void setDaysSince(Integer daysSince) { this.daysSince = daysSince; }
    }

    public static class DayReminderDueResponse {
        private List<DayReminderDueItem> items;
        private int total;

        public List<DayReminderDueItem> getItems() { return items; }
        public void setItems(List<DayReminderDueItem> items) { this.items = items; }
        public int getTotal() { return total; }
        public void setTotal(int total) { this.total = total; }
    }

    public static class DeleteDayResponse {
        private boolean deleted;

        public boolean isDeleted() { return deleted; }
        public void setDeleted(boolean deleted) { this.deleted = deleted; }
    }
}
