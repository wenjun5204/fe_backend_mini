package com.example.backend.service;

import com.example.backend.common.ApiException;
import com.example.backend.common.CurrentUser;
import com.example.backend.dto.DutyDtos.DutyCalendarResponse;
import com.example.backend.dto.DutyDtos.DutyMonthStats;
import com.example.backend.dto.DutyDtos.DutyNextDuty;
import com.example.backend.dto.DutyDtos.DutyRemindConfig;
import com.example.backend.dto.DutyDtos.DutyRemindConfigRequest;
import com.example.backend.dto.DutyDtos.DutyRuleApplyRequest;
import com.example.backend.dto.DutyDtos.DutyRuleApplyResponse;
import com.example.backend.dto.DutyDtos.DutyRuleView;
import com.example.backend.dto.DutyDtos.DutyShiftClearResponse;
import com.example.backend.dto.DutyDtos.DutyShiftRangeClearResponse;
import com.example.backend.dto.DutyDtos.DutyShiftRequest;
import com.example.backend.dto.DutyDtos.DutyShiftView;
import com.example.backend.dto.DutyDtos.DutyTodayResponse;
import com.example.backend.entity.DutyRemindEntity;
import com.example.backend.entity.DutyRuleEntity;
import com.example.backend.entity.DutyShiftEntity;
import com.example.backend.repository.DutyRemindRepository;
import com.example.backend.repository.DutyRuleRepository;
import com.example.backend.repository.DutyShiftRepository;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 值班日历(契约 v1.9.0 tag=duty):个人视角排班记录与提醒配置。
 * 口径(需求 spec D1/D5-D9):
 * - 用户 × 日期一条(uk 物理保证),单日编辑 upsert、批量排班覆盖写入;
 * - 周期规则用户级单条生效,重新应用整行覆盖;删除单日不触碰规则本体;
 * - 下次值班=从明天起 60 天内最近非「休」班次日(今天不参与倒计时);
 * - 提醒只存时机配置,真实 subscribeMessage.send 属部署联调阶段(零推送调用,验收红线)。
 * MySQL 严格模式铁律:readOnly 方法内零写库。
 */
@Service
public class DutyService {

    /** 班次值域:early 早班/mid 中班/night 夜班/rest 休 */
    private static final Set<String> SHIFTS = Set.of("early", "mid", "night", "rest");

    /** 规则类型值域 */
    private static final Set<String> RULE_TYPES = Set.of("daily", "on1off1", "on2off2", "emnr", "weekday");

    /** 提醒时机值域:advance_1d 提前1天12:00(默认)/same_day_08 当天08:00/advance_2h 提前2小时 */
    private static final Set<String> TIMINGS = Set.of("advance_1d", "same_day_08", "advance_2h");

    /** 序列规则的循环序列(从开始日逐日循环填充;休也是显式记录,一并写入) */
    private static final Map<String, List<String>> SEQUENCES = Map.of(
            "daily", List.of("early"),
            "on1off1", List.of("early", "rest"),
            "on2off2", List.of("early", "early", "rest", "rest"),
            "emnr", List.of("early", "mid", "night", "rest"));

    /** 下次值班扫描窗口:从明天起 60 天(含) */
    private static final int NEXT_DUTY_WINDOW_DAYS = 60;

    /** 规则应用跨度上限:超过截断至 365 天(含首尾) */
    private static final int MAX_SPAN_DAYS = 365;

    private final DutyShiftRepository shiftRepository;
    private final DutyRuleRepository ruleRepository;
    private final DutyRemindRepository remindRepository;

    public DutyService(DutyShiftRepository shiftRepository,
                       DutyRuleRepository ruleRepository,
                       DutyRemindRepository remindRepository) {
        this.shiftRepository = shiftRepository;
        this.ruleRepository = ruleRepository;
        this.remindRepository = remindRepository;
    }

    // ---- 月历 ----

    /** 月历数据:该月有排班的日期(升序)+ 各班次统计;month 缺省当月 */
    @Transactional(readOnly = true)
    public DutyCalendarResponse getCalendar(String month) {
        Long userId = CurrentUser.get();
        YearMonth target = parseMonth(month);
        LocalDate from = target.atDay(1);
        LocalDate to = target.atEndOfMonth();
        List<DutyShiftEntity> rows =
                shiftRepository.findByUserIdAndDutyDateBetweenOrderByDutyDateAsc(userId, from, to);

        DutyMonthStats stats = new DutyMonthStats();
        List<DutyShiftView> shifts = new ArrayList<>();
        for (DutyShiftEntity row : rows) {
            shifts.add(toView(row));
            switch (row.getShift()) {
                case "early" -> stats.setEarly(stats.getEarly() + 1);
                case "mid" -> stats.setMid(stats.getMid() + 1);
                case "night" -> stats.setNight(stats.getNight() + 1);
                case "rest" -> stats.setRest(stats.getRest() + 1);
                default -> { /* 值域外的历史脏行不计入统计 */ }
            }
        }
        DutyCalendarResponse response = new DutyCalendarResponse();
        response.setMonth(target.toString());
        response.setShifts(shifts);
        response.setStats(stats);
        return response;
    }

    // ---- 今日状态卡 ----

    /** 今日班次 + 下次值班(明天起 60 天内最近非休)+ 提醒配置;date 缺省当天(集成测试注入时钟) */
    @Transactional(readOnly = true)
    public DutyTodayResponse getToday(String date) {
        Long userId = CurrentUser.get();
        LocalDate today = parseDate(date == null || date.isBlank() ? LocalDate.now().toString() : date);

        DutyTodayResponse response = new DutyTodayResponse();
        response.setDate(today.toString());
        response.setTodayShift(shiftRepository.findByUserIdAndDutyDate(userId, today)
                .map(DutyShiftEntity::getShift).orElse(null));
        response.setNextDuty(findNextDuty(userId, today));
        response.setRemind(getRemindConfig(userId));
        return response;
    }

    /** 从明天起 60 天内最近一个非「休」班次日;无则 null(今天不参与倒计时,状态卡已展示) */
    private DutyNextDuty findNextDuty(Long userId, LocalDate today) {
        LocalDate from = today.plusDays(1);
        LocalDate to = today.plusDays(NEXT_DUTY_WINDOW_DAYS);
        for (DutyShiftEntity row : shiftRepository
                .findByUserIdAndDutyDateBetweenOrderByDutyDateAsc(userId, from, to)) {
            if (!"rest".equals(row.getShift())) {
                DutyNextDuty next = new DutyNextDuty();
                next.setDate(row.getDutyDate().toString());
                next.setShift(row.getShift());
                next.setDaysUntil((int) ChronoUnit.DAYS.between(today, row.getDutyDate()));
                return next;
            }
        }
        return null;
    }

    // ---- 单日编辑 ----

    /** 单日设置(upsert):已有行(不论来源)直接覆盖为 manual;点选即保存 */
    @Transactional
    public DutyShiftView setShift(DutyShiftRequest request) {
        Long userId = CurrentUser.get();
        LocalDate date = parseDate(request == null ? null : request.getDate());
        String shift = normalizeEnum(request == null ? null : request.getShift(), SHIFTS, "班次不对,请您重新选择");

        DutyShiftEntity entity = shiftRepository.findByUserIdAndDutyDate(userId, date).orElse(null);
        if (entity == null) {
            entity = new DutyShiftEntity();
            entity.setUserId(userId);
            entity.setDutyDate(date);
        }
        entity.setShift(shift);
        entity.setSource("manual");
        entity.setUpdatedAt(LocalDateTime.now());
        return toView(shiftRepository.save(entity));
    }

    /** 清除这一天(幂等:无排班也成功);不触碰周期规则本体,重新应用规则会回填 */
    @Transactional
    public DutyShiftClearResponse clearShift(String date) {
        Long userId = CurrentUser.get();
        LocalDate target = parseDate(date);
        shiftRepository.deleteByUserIdAndDutyDate(userId, target);
        DutyShiftClearResponse response = new DutyShiftClearResponse();
        response.setDate(target.toString());
        response.setCleared(true);
        return response;
    }

    /**
     * 范围清除(批量撤销):删除 from..to 段内本人全部排班(不论来源);幂等(段内无排班 clearedDays=0);
     * 跨度超 366 天截断至 366 天(与规则应用 MAX_SPAN_DAYS 对称);不触碰周期规则本体。
     */
    @Transactional
    public DutyShiftRangeClearResponse clearShiftRange(String from, String to) {
        Long userId = CurrentUser.get();
        LocalDate start = parseDate(from);
        LocalDate end = parseDate(to);
        if (end.isBefore(start)) {
            throw ApiException.param("截止日期不能早于开始日期");
        }
        if (ChronoUnit.DAYS.between(start, end) + 1 > MAX_SPAN_DAYS + 1) {
            end = start.plusDays(MAX_SPAN_DAYS);
        }
        List<DutyShiftEntity> rows = shiftRepository
                .findByUserIdAndDutyDateBetweenOrderByDutyDateAsc(userId, start, end);
        shiftRepository.deleteAllInBatch(rows);
        DutyShiftRangeClearResponse response = new DutyShiftRangeClearResponse();
        response.setFrom(start.toString());
        response.setTo(end.toString());
        response.setClearedDays(rows.size());
        return response;
    }

    // ---- 周期规则 ----

    /** 当前生效规则;无规则返回 active=false 空壳(弹层引导「从批量排班开始」) */
    @Transactional(readOnly = true)
    public DutyRuleView getRule() {
        Long userId = CurrentUser.get();
        DutyRuleView view = new DutyRuleView();
        view.setActive(false);
        view.setAppliedDays(0);
        ruleRepository.findByUserId(userId).ifPresent(rule -> {
            view.setRuleType(rule.getRuleType());
            view.setStartDate(rule.getStartDate().toString());
            view.setEndDate(rule.getEndDate().toString());
            view.setActive(rule.isActive());
            view.setAppliedDays(rule.getAppliedDays());
        });
        return view;
    }

    /**
     * 批量排班:序列规则从 startDate 循环填充至 endDate(含休班);
     * weekday 规则只填命中星期五的日期(其余日不动);范围内已有排班直接覆盖;
     * 跨度 >365 天截断;同时 upsert 单条生效规则行。覆盖确认由前端二次弹窗承担。
     */
    @Transactional
    public DutyRuleApplyResponse applyRule(DutyRuleApplyRequest request) {
        Long userId = CurrentUser.get();
        String ruleType = normalizeEnum(request == null ? null : request.getRuleType(), RULE_TYPES, "请您选择一个排班规则");
        LocalDate start = parseDate(request == null ? null : request.getStartDate());
        LocalDate end = parseDate(request == null ? null : request.getEndDate());
        if (end.isBefore(start)) {
            throw ApiException.param("截止日期不能早于开始日期");
        }

        boolean truncated = false;
        if (ChronoUnit.DAYS.between(start, end) + 1 > MAX_SPAN_DAYS) {
            end = start.plusDays(MAX_SPAN_DAYS - 1);
            truncated = true;
        }

        List<DutyShiftEntity> rows;
        String source = "rule:" + ruleType;
        if ("weekday".equals(ruleType)) {
            rows = applyWeekday(userId, start, end, source);
        } else {
            rows = applySequence(userId, start, end, SEQUENCES.get(ruleType), source);
        }
        int appliedDays = rows.size();
        shiftRepository.saveAll(rows);

        DutyRuleEntity rule = ruleRepository.findByUserId(userId).orElse(null);
        if (rule == null) {
            rule = new DutyRuleEntity();
            rule.setUserId(userId);
        }
        rule.setRuleType(ruleType);
        rule.setStartDate(start);
        rule.setEndDate(end);
        rule.setActive(true);
        rule.setAppliedDays(appliedDays);
        rule.setUpdatedAt(LocalDateTime.now());
        ruleRepository.save(rule);

        DutyRuleApplyResponse response = new DutyRuleApplyResponse();
        response.setRuleType(ruleType);
        response.setStartDate(start.toString());
        response.setEndDate(end.toString());
        response.setAppliedDays(appliedDays);
        response.setTruncated(truncated);
        return response;
    }

    /** 序列规则:逐日循环填充(含已有行覆盖;区间内已有排班不论来源一并覆盖) */
    private List<DutyShiftEntity> applySequence(Long userId, LocalDate start, LocalDate end,
                                                List<String> sequence, String source) {
        Map<LocalDate, DutyShiftEntity> existing = loadExisting(userId, start, end);
        List<DutyShiftEntity> rows = new ArrayList<>();
        LocalDate cursor = start;
        int index = 0;
        while (!cursor.isAfter(end)) {
            rows.add(upsertRow(userId, cursor, sequence.get(index % sequence.size()), source, existing));
            cursor = cursor.plusDays(1);
            index++;
        }
        return rows;
    }

    /** weekday 规则:仅星期五写早班,其余日不动(序列外的日期保持原状) */
    private List<DutyShiftEntity> applyWeekday(Long userId, LocalDate start, LocalDate end, String source) {
        Map<LocalDate, DutyShiftEntity> existing = loadExisting(userId, start, end);
        List<DutyShiftEntity> rows = new ArrayList<>();
        LocalDate cursor = start;
        while (!cursor.isAfter(end)) {
            if (cursor.getDayOfWeek() == DayOfWeek.FRIDAY) {
                rows.add(upsertRow(userId, cursor, "early", source, existing));
            }
            cursor = cursor.plusDays(1);
        }
        return rows;
    }

    private Map<LocalDate, DutyShiftEntity> loadExisting(Long userId, LocalDate start, LocalDate end) {
        Map<LocalDate, DutyShiftEntity> map = new HashMap<>();
        for (DutyShiftEntity row : shiftRepository
                .findByUserIdAndDutyDateBetweenOrderByDutyDateAsc(userId, start, end)) {
            map.put(row.getDutyDate(), row);
        }
        return map;
    }

    private DutyShiftEntity upsertRow(Long userId, LocalDate date, String shift, String source,
                                      Map<LocalDate, DutyShiftEntity> existing) {
        DutyShiftEntity entity = existing.get(date);
        if (entity == null) {
            entity = new DutyShiftEntity();
            entity.setUserId(userId);
            entity.setDutyDate(date);
        }
        entity.setShift(shift);
        entity.setSource(source);
        entity.setUpdatedAt(LocalDateTime.now());
        return entity;
    }

    // ---- 提醒配置 ----

    /** 保存提醒时机与意愿开关;授权额度走既有 NotifyService 账本,本方法零推送调用 */
    @Transactional
    public DutyRemindConfig updateRemindConfig(DutyRemindConfigRequest request) {
        Long userId = CurrentUser.get();
        String timing = normalizeEnum(request == null ? null : request.getTiming(), TIMINGS, "请您选择提醒时间");

        DutyRemindEntity entity = remindRepository.findByUserId(userId).orElse(null);
        if (entity == null) {
            entity = new DutyRemindEntity();
            entity.setUserId(userId);
        }
        entity.setTiming(timing);
        entity.setEnabled(request != null && request.isEnabled());
        entity.setUpdatedAt(LocalDateTime.now());
        remindRepository.save(entity);
        return toRemindConfig(entity);
    }

    /** 提醒配置读取(无行=默认 advance_1d 且 enabled=false) */
    private DutyRemindConfig getRemindConfig(Long userId) {
        return remindRepository.findByUserId(userId).map(this::toRemindConfig)
                .orElseGet(() -> {
                    DutyRemindConfig config = new DutyRemindConfig();
                    config.setTiming("advance_1d");
                    config.setEnabled(false);
                    return config;
                });
    }

    private DutyRemindConfig toRemindConfig(DutyRemindEntity entity) {
        DutyRemindConfig config = new DutyRemindConfig();
        config.setTiming(entity.getTiming());
        config.setEnabled(entity.isEnabled());
        return config;
    }

    // ---- 通用 ----

    private DutyShiftView toView(DutyShiftEntity entity) {
        DutyShiftView view = new DutyShiftView();
        view.setDate(entity.getDutyDate().toString());
        view.setShift(entity.getShift());
        view.setSource(entity.getSource());
        return view;
    }

    private static LocalDate parseDate(String raw) {
        if (raw == null || raw.isBlank()) {
            throw ApiException.param("请您选择一个日期");
        }
        try {
            return LocalDate.parse(raw.trim());
        } catch (DateTimeParseException e) {
            throw ApiException.param("日期格式不对,请重试");
        }
    }

    private static YearMonth parseMonth(String raw) {
        String value = raw == null || raw.isBlank() ? YearMonth.now().toString() : raw.trim();
        if (!value.matches("\\d{4}-\\d{2}")) {
            throw ApiException.param("月份格式不对,请重试");
        }
        try {
            return YearMonth.parse(value);
        } catch (DateTimeParseException e) {
            throw ApiException.param("月份格式不对,请重试");
        }
    }

    private static String normalizeEnum(String raw, Set<String> domain, String message) {
        String value = raw == null ? "" : raw.trim();
        if (!domain.contains(value)) {
            throw ApiException.param(message);
        }
        return value;
    }
}
