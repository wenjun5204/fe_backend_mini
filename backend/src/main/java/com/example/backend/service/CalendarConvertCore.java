package com.example.backend.service;

import com.example.backend.common.ApiException;
import com.example.backend.dto.BirthdayDtos.CalendarType;
import net.time4j.PlainDate;
import net.time4j.calendar.ChineseCalendar;
import net.time4j.calendar.EastAsianMonth;
import net.time4j.calendar.EastAsianYear;
import net.time4j.engine.EpochDays;
import java.time.LocalDate;

/**
 * 公农历换算核心(生日簿/日子管家共用,单点维护,调用方不得另持换算副本)。
 * 由 BirthdayService 私有方法群上移而来,行为零变化;BirthdayServiceTest 为回归护栏。
 */
public final class CalendarConvertCore {

    private CalendarConvertCore() {
    }

    /** 下次落点主入口(以今天为基准;生日簿口径) */
    public static LocalDate nextDate(CalendarType calendarType, Integer month, Integer day, boolean isLeapMonth) {
        return nextDate(calendarType, month, day, isLeapMonth, LocalDate.now());
    }

    /** 下次落点主入口(自定义基准日;提醒到期计算按给定日期试算) */
    public static LocalDate nextDate(CalendarType calendarType, Integer month, Integer day, boolean isLeapMonth,
                                     LocalDate today) {
        if (month == null || day == null) {
            throw ApiException.param("请选择生日的月和日");
        }
        if (calendarType == CalendarType.SOLAR) {
            LocalDate candidate = solarCandidate(today.getYear(), month, day);
            return candidate.isBefore(today) ? candidate.plusYears(1) : candidate;
        }
        // 闰月生日:优先找窗口年份内真正的闰月;都没有时按平常月同日过
        for (int year = today.getYear() - 1; year <= today.getYear() + 2; year++) {
            LocalDate candidate = lunarCandidate(year, month, day, isLeapMonth);
            if (candidate != null && !candidate.isBefore(today)) {
                return candidate;
            }
        }
        if (isLeapMonth) {
            for (int year = today.getYear(); year <= today.getYear() + 2; year++) {
                LocalDate candidate = lunarCandidate(year, month, day, false);
                if (candidate != null && !candidate.isBefore(today)) {
                    return candidate;
                }
            }
        }
        throw ApiException.param("请选择正确的生日日期");
    }

    /** 某公历年该月日的日期;2 月 29 在平年降 2 月 28,其余非法日期抛参数错误 */
    public static LocalDate solarCandidate(int year, Integer month, Integer day) {
        try {
            return LocalDate.of(year, month, day);
        } catch (java.time.DateTimeException e) {
            // 2 月 29 日生日在平年按 2 月 28 日过
            if (month == 2 && day == 29) {
                return LocalDate.of(year, 2, 28);
            }
            throw ApiException.param("请选择正确的生日日期");
        }
    }

    /** 某农历年该月日的公历日期;该年无此闰月或日期不存在时返回 null */
    public static LocalDate lunarCandidate(int year, Integer month, Integer day, boolean isLeapMonth) {
        try {
            EastAsianMonth lunarMonth = EastAsianMonth.valueOf(month);
            if (isLeapMonth) {
                lunarMonth = lunarMonth.withLeap();
            }
            return fromChineseDate(ChineseCalendar.of(EastAsianYear.forGregorian(year), lunarMonth, day));
        } catch (RuntimeException e) {
            return null;
        }
    }

    /** 「农历八月十五」式显示文本 */
    public static String lunarText(LocalDate date) {
        return lunarText(chineseDate(date));
    }

    public static String lunarText(ChineseCalendar lunar) {
        String prefix = lunar.getMonth().isLeap() ? "闰" : "";
        return "农历" + prefix + lunar.getMonth().getDisplayName(java.util.Locale.SIMPLIFIED_CHINESE,
                net.time4j.format.NumberSystem.ARABIC) + "月" + lunar.getDayOfMonth() + "日";
    }

    public static ChineseCalendar chineseDate(LocalDate date) {
        return ChineseCalendar.axis().getCalendarSystem().transform(
                PlainDate.from(date).getDaysSinceEpochUTC());
    }

    public static LocalDate fromChineseDate(ChineseCalendar lunar) {
        long utcDays = lunar.getDaysSinceEpochUTC();
        return PlainDate.of(utcDays, EpochDays.UTC).toTemporalAccessor();
    }
}
