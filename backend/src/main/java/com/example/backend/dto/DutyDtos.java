package com.example.backend.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

/**
 * 值班日历契约模型(v1.9.0 tag=duty)。
 * shift/ruleType/timing 在请求中为 String 承接,由 DutyService 层校验值域(非法 40000);
 * 响应回显同字符串形态,与契约枚举序列化一致。
 * 枚举常量名与契约 wire 值同为小写(PhotoDtos 先例:字段挂 @Schema(implementation=枚举) 让
 * springdoc 在属性上内联导出枚举值;命名模型不注册是显式豁免的 wire 等价)。
 */
public final class DutyDtos {

    private DutyDtos() {
    }

    /** 班次:early 早班/mid 中班/night 夜班/rest 休 */
    public enum DutyShiftKind {
        early, mid, night, rest
    }

    /** 规则类型:daily/on1off1/on2off2/emnr/weekday */
    public enum DutyRuleType {
        daily, on1off1, on2off2, emnr, weekday
    }

    /** 提醒时机:advance_1d(默认)/same_day_08/advance_2h */
    public enum DutyRemindTiming {
        advance_1d, same_day_08, advance_2h
    }

    // ---- 单日设置 ----

    public static class DutyShiftRequest {
        private String date;
        @Schema(implementation = DutyShiftKind.class)
        private String shift;

        public String getDate() { return date; }
        public void setDate(String date) { this.date = date; }
        public String getShift() { return shift; }
        public void setShift(String shift) { this.shift = shift; }
    }

    public static class DutyShiftView {
        private String date;
        @Schema(implementation = DutyShiftKind.class)
        private String shift;
        private String source;

        public String getDate() { return date; }
        public void setDate(String date) { this.date = date; }
        public String getShift() { return shift; }
        public void setShift(String shift) { this.shift = shift; }
        public String getSource() { return source; }
        public void setSource(String source) { this.source = source; }
    }

    public static class DutyShiftClearResponse {
        private String date;
        private boolean cleared;

        public String getDate() { return date; }
        public void setDate(String date) { this.date = date; }
        public boolean isCleared() { return cleared; }
        public void setCleared(boolean cleared) { this.cleared = cleared; }
    }

    public static class DutyShiftRangeClearResponse {
        private String from;
        private String to;
        private int clearedDays;

        public String getFrom() { return from; }
        public void setFrom(String from) { this.from = from; }
        public String getTo() { return to; }
        public void setTo(String to) { this.to = to; }
        public int getClearedDays() { return clearedDays; }
        public void setClearedDays(int clearedDays) { this.clearedDays = clearedDays; }
    }

    // ---- 月历 ----

    public static class DutyMonthStats {
        private int early;
        private int mid;
        private int night;
        private int rest;

        public int getEarly() { return early; }
        public void setEarly(int early) { this.early = early; }
        public int getMid() { return mid; }
        public void setMid(int mid) { this.mid = mid; }
        public int getNight() { return night; }
        public void setNight(int night) { this.night = night; }
        public int getRest() { return rest; }
        public void setRest(int rest) { this.rest = rest; }
    }

    public static class DutyCalendarResponse {
        private String month;
        private List<DutyShiftView> shifts;
        private DutyMonthStats stats;

        public String getMonth() { return month; }
        public void setMonth(String month) { this.month = month; }
        public List<DutyShiftView> getShifts() { return shifts; }
        public void setShifts(List<DutyShiftView> shifts) { this.shifts = shifts; }
        public DutyMonthStats getStats() { return stats; }
        public void setStats(DutyMonthStats stats) { this.stats = stats; }
    }

    // ---- 今日状态卡 ----

    public static class DutyNextDuty {
        private String date;
        @Schema(implementation = DutyShiftKind.class)
        private String shift;
        private int daysUntil;

        public String getDate() { return date; }
        public void setDate(String date) { this.date = date; }
        public String getShift() { return shift; }
        public void setShift(String shift) { this.shift = shift; }
        public int getDaysUntil() { return daysUntil; }
        public void setDaysUntil(int daysUntil) { this.daysUntil = daysUntil; }
    }

    public static class DutyRemindConfig {
        @Schema(implementation = DutyRemindTiming.class)
        private String timing;
        private boolean enabled;

        public String getTiming() { return timing; }
        public void setTiming(String timing) { this.timing = timing; }
        public boolean isEnabled() { return enabled; }
        public void setEnabled(boolean enabled) { this.enabled = enabled; }
    }

    public static class DutyTodayResponse {
        private String date;
        @Schema(implementation = DutyShiftKind.class, nullable = true)
        private String todayShift;
        private DutyNextDuty nextDuty;
        private DutyRemindConfig remind;

        public String getDate() { return date; }
        public void setDate(String date) { this.date = date; }
        public String getTodayShift() { return todayShift; }
        public void setTodayShift(String todayShift) { this.todayShift = todayShift; }
        public DutyNextDuty getNextDuty() { return nextDuty; }
        public void setNextDuty(DutyNextDuty nextDuty) { this.nextDuty = nextDuty; }
        public DutyRemindConfig getRemind() { return remind; }
        public void setRemind(DutyRemindConfig remind) { this.remind = remind; }
    }

    // ---- 分享(契约 v1.9.1,PRD 5.7 P1) ----

    public static class DutyShareSceneResponse {
        private String scene;
        private String pagePath;

        public String getScene() { return scene; }
        public void setScene(String scene) { this.scene = scene; }
        public String getPagePath() { return pagePath; }
        public void setPagePath(String pagePath) { this.pagePath = pagePath; }
    }

    public static class DutySharedCalendarResponse {
        private String month;
        private String ownerName;
        private List<DutyShiftView> shifts;
        private DutyMonthStats stats;

        public String getMonth() { return month; }
        public void setMonth(String month) { this.month = month; }
        public String getOwnerName() { return ownerName; }
        public void setOwnerName(String ownerName) { this.ownerName = ownerName; }
        public List<DutyShiftView> getShifts() { return shifts; }
        public void setShifts(List<DutyShiftView> shifts) { this.shifts = shifts; }
        public DutyMonthStats getStats() { return stats; }
        public void setStats(DutyMonthStats stats) { this.stats = stats; }
    }

    // ---- 周期规则 ----

    public static class DutyRuleView {
        @Schema(implementation = DutyRuleType.class, nullable = true)
        private String ruleType;
        private String startDate;
        private String endDate;
        private boolean active;
        private int appliedDays;

        public String getRuleType() { return ruleType; }
        public void setRuleType(String ruleType) { this.ruleType = ruleType; }
        public String getStartDate() { return startDate; }
        public void setStartDate(String startDate) { this.startDate = startDate; }
        public String getEndDate() { return endDate; }
        public void setEndDate(String endDate) { this.endDate = endDate; }
        public boolean isActive() { return active; }
        public void setActive(boolean active) { this.active = active; }
        public int getAppliedDays() { return appliedDays; }
        public void setAppliedDays(int appliedDays) { this.appliedDays = appliedDays; }
    }

    public static class DutyRuleApplyRequest {
        @Schema(implementation = DutyRuleType.class)
        private String ruleType;
        private String startDate;
        private String endDate;

        public String getRuleType() { return ruleType; }
        public void setRuleType(String ruleType) { this.ruleType = ruleType; }
        public String getStartDate() { return startDate; }
        public void setStartDate(String startDate) { this.startDate = startDate; }
        public String getEndDate() { return endDate; }
        public void setEndDate(String endDate) { this.endDate = endDate; }
    }

    public static class DutyRuleApplyResponse {
        @Schema(implementation = DutyRuleType.class)
        private String ruleType;
        private String startDate;
        private String endDate;
        private int appliedDays;
        private boolean truncated;

        public String getRuleType() { return ruleType; }
        public void setRuleType(String ruleType) { this.ruleType = ruleType; }
        public String getStartDate() { return startDate; }
        public void setStartDate(String startDate) { this.startDate = startDate; }
        public String getEndDate() { return endDate; }
        public void setEndDate(String endDate) { this.endDate = endDate; }
        public int getAppliedDays() { return appliedDays; }
        public void setAppliedDays(int appliedDays) { this.appliedDays = appliedDays; }
        public boolean isTruncated() { return truncated; }
        public void setTruncated(boolean truncated) { this.truncated = truncated; }
    }

    // ---- 提醒配置 ----

    public static class DutyRemindConfigRequest {
        @Schema(implementation = DutyRemindTiming.class)
        private String timing;
        private boolean enabled;

        public String getTiming() { return timing; }
        public void setTiming(String timing) { this.timing = timing; }
        public boolean isEnabled() { return enabled; }
        public void setEnabled(boolean enabled) { this.enabled = enabled; }
    }
}
