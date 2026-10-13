package com.example.backend.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

/** 家庭生日簿契约模型；仅保存月、日和日期口径，不保存出生年份或其他非必要信息。 */
public final class BirthdayDtos {

    private BirthdayDtos() {
    }

    public enum CalendarType { SOLAR, LUNAR }
    public enum SubjectType { MEMBER, CONTACT }
    public enum Visibility { PRIVATE, FAMILY }

    public static class CalendarConvertRequest {
        private CalendarType calendarType;
        private String solarDate;
        private Integer month;
        private Integer day;
        private Boolean isLeapMonth;

        public CalendarType getCalendarType() { return calendarType; }
        public void setCalendarType(CalendarType calendarType) { this.calendarType = calendarType; }
        public String getSolarDate() { return solarDate; }
        public void setSolarDate(String solarDate) { this.solarDate = solarDate; }
        public Integer getMonth() { return month; }
        public void setMonth(Integer month) { this.month = month; }
        public Integer getDay() { return day; }
        public void setDay(Integer day) { this.day = day; }
        public Boolean getIsLeapMonth() { return isLeapMonth; }
        public void setIsLeapMonth(Boolean isLeapMonth) { this.isLeapMonth = isLeapMonth; }
    }

    public static class CalendarConvertResponse {
        private String solarDate;
        private String lunarText;
        private int lunarMonth;
        private int lunarDay;
        private boolean isLeapMonth;

        public String getSolarDate() { return solarDate; }
        public void setSolarDate(String solarDate) { this.solarDate = solarDate; }
        public String getLunarText() { return lunarText; }
        public void setLunarText(String lunarText) { this.lunarText = lunarText; }
        public int getLunarMonth() { return lunarMonth; }
        public void setLunarMonth(int lunarMonth) { this.lunarMonth = lunarMonth; }
        public int getLunarDay() { return lunarDay; }
        public void setLunarDay(int lunarDay) { this.lunarDay = lunarDay; }
        @JsonProperty("isLeapMonth")
        public boolean isLeapMonth() { return isLeapMonth; }
        public void setLeapMonth(boolean leapMonth) { isLeapMonth = leapMonth; }
    }

    public static class BirthdayUpsertRequest {
        private SubjectType subjectType;
        private Long subjectUserId;
        private String displayName;
        private CalendarType calendarType;
        private Integer month;
        private Integer day;
        private Boolean isLeapMonth;
        private Visibility visibility;

        public SubjectType getSubjectType() { return subjectType; }
        public void setSubjectType(SubjectType subjectType) { this.subjectType = subjectType; }
        public Long getSubjectUserId() { return subjectUserId; }
        public void setSubjectUserId(Long subjectUserId) { this.subjectUserId = subjectUserId; }
        public String getDisplayName() { return displayName; }
        public void setDisplayName(String displayName) { this.displayName = displayName; }
        public CalendarType getCalendarType() { return calendarType; }
        public void setCalendarType(CalendarType calendarType) { this.calendarType = calendarType; }
        public Integer getMonth() { return month; }
        public void setMonth(Integer month) { this.month = month; }
        public Integer getDay() { return day; }
        public void setDay(Integer day) { this.day = day; }
        public Boolean getIsLeapMonth() { return isLeapMonth; }
        public void setIsLeapMonth(Boolean isLeapMonth) { this.isLeapMonth = isLeapMonth; }
        public Visibility getVisibility() { return visibility; }
        public void setVisibility(Visibility visibility) { this.visibility = visibility; }
    }

    public static class BirthdayReminderRequest {
        private List<Integer> reminderDays;

        public List<Integer> getReminderDays() { return reminderDays; }
        public void setReminderDays(List<Integer> reminderDays) { this.reminderDays = reminderDays; }
    }

    public static class BirthdayReminder {
        private Long birthdayId;
        private List<Integer> reminderDays;

        public Long getBirthdayId() { return birthdayId; }
        public void setBirthdayId(Long birthdayId) { this.birthdayId = birthdayId; }
        public List<Integer> getReminderDays() { return reminderDays; }
        public void setReminderDays(List<Integer> reminderDays) { this.reminderDays = reminderDays; }
    }

    public static class BirthdayRecord {
        private Long id;
        private Long familyId;
        private SubjectType subjectType;
        private Long subjectUserId;
        private String displayName;
        private CalendarType calendarType;
        private int month;
        private int day;
        private boolean isLeapMonth;
        private Visibility visibility;
        private Long createdBy;
        private String nextBirthdayDate;
        private String nextBirthdayLunarText;
        private long daysUntil;
        private List<Integer> reminderDays;

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
        @JsonProperty("isLeapMonth")
        public boolean isLeapMonth() { return isLeapMonth; }
        public void setLeapMonth(boolean leapMonth) { isLeapMonth = leapMonth; }
        public Visibility getVisibility() { return visibility; }
        public void setVisibility(Visibility visibility) { this.visibility = visibility; }
        public Long getCreatedBy() { return createdBy; }
        public void setCreatedBy(Long createdBy) { this.createdBy = createdBy; }
        public String getNextBirthdayDate() { return nextBirthdayDate; }
        public void setNextBirthdayDate(String nextBirthdayDate) { this.nextBirthdayDate = nextBirthdayDate; }
        public String getNextBirthdayLunarText() { return nextBirthdayLunarText; }
        public void setNextBirthdayLunarText(String nextBirthdayLunarText) { this.nextBirthdayLunarText = nextBirthdayLunarText; }
        public long getDaysUntil() { return daysUntil; }
        public void setDaysUntil(long daysUntil) { this.daysUntil = daysUntil; }
        public List<Integer> getReminderDays() { return reminderDays; }
        public void setReminderDays(List<Integer> reminderDays) { this.reminderDays = reminderDays; }
    }

    public static class BirthdayListResponse {
        private List<BirthdayRecord> items;

        public List<BirthdayRecord> getItems() { return items; }
        public void setItems(List<BirthdayRecord> items) { this.items = items; }
    }

    public static class UpcomingBirthdayResponse {
        private BirthdayRecord upcoming;

        public BirthdayRecord getUpcoming() { return upcoming; }
        public void setUpcoming(BirthdayRecord upcoming) { this.upcoming = upcoming; }
    }

    public static class DeleteBirthdayResponse {
        private boolean deleted;

        public boolean isDeleted() { return deleted; }
        public void setDeleted(boolean deleted) { this.deleted = deleted; }
    }
}
