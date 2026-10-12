package com.example.backend.service;

import com.example.backend.common.ApiException;
import com.example.backend.common.CurrentUser;
import com.example.backend.dto.BirthdayDtos.CalendarType;
import com.example.backend.dto.DayDtos.DayKind;
import com.example.backend.dto.DayDtos.DayListResponse;
import com.example.backend.dto.DayDtos.DayRecord;
import com.example.backend.dto.DayDtos.DayReminder;
import com.example.backend.dto.DayDtos.DayReminderDueItem;
import com.example.backend.dto.DayDtos.DayReminderDueResponse;
import com.example.backend.dto.DayDtos.DayReminderRequest;
import com.example.backend.dto.DayDtos.DayUpsertRequest;
import com.example.backend.dto.DayDtos.DayVisibility;
import com.example.backend.dto.DayDtos.DeleteDayResponse;
import com.example.backend.entity.DayRecordEntity;
import com.example.backend.entity.DayReminderEntity;
import com.example.backend.entity.FamilyMemberEntity;
import com.example.backend.repository.DayRecordRepository;
import com.example.backend.repository.DayReminderRepository;
import com.example.backend.repository.FamilyMemberRepository;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 念家时·日子管家:落点/相伴天数实时计算不落库;提醒按用户独立配置。
 * 越权一律 40400(不泄露存在性);无家族用户不拦截,FAMILY 降级 PRIVATE;
 * LUNAR 闰月固定 false(仅按平常月/日数值匹配,换算核心与生日簿单点共用)。
 */
@Service
public class DayService {

    private static final List<Integer> ALLOWED_REMINDER_OFFSETS = List.of(7, 3, 0);

    /** relation 缺省 emoji 预置映射(与前端 days-data.js 同表,双端各持一份) */
    private static final Map<String, String> DEFAULT_EMOJIS = Map.of(
            "妈妈", "👩", "爸爸", "👨", "老伴", "👴", "儿子", "🧑", "女儿", "👧",
            "孙子", "👦", "孙女", "👧", "亲家", "🤝", "我自己", "😊");

    private final DayRecordRepository dayRecordRepository;
    private final DayReminderRepository dayReminderRepository;
    private final FamilyMemberRepository familyMemberRepository;

    public DayService(DayRecordRepository dayRecordRepository,
                      DayReminderRepository dayReminderRepository,
                      FamilyMemberRepository familyMemberRepository) {
        this.dayRecordRepository = dayRecordRepository;
        this.dayReminderRepository = dayReminderRepository;
        this.familyMemberRepository = familyMemberRepository;
    }

    @Transactional(readOnly = true)
    public DayListResponse getDays() {
        Long userId = CurrentUser.get();
        Long familyId = myFamilyId(userId);
        List<DayRecord> days = dayRecordRepository.findVisible(userId, familyId).stream()
                .map(record -> toDto(record, userId))
                .toList();
        // 服务端排好:非 TOGETHER 按 daysUntil 升序在前,TOGETHER 固定底部,前端直接渲染
        List<DayRecord> sorted = new ArrayList<>();
        days.stream()
                .filter(day -> day.getKind() != DayKind.TOGETHER)
                .sorted(Comparator.comparing(DayRecord::getDaysUntil))
                .forEach(sorted::add);
        days.stream().filter(day -> day.getKind() == DayKind.TOGETHER).forEach(sorted::add);
        DayListResponse response = new DayListResponse();
        response.setItems(sorted);
        return response;
    }

    @Transactional
    public DayRecord create(DayUpsertRequest request) {
        Long userId = CurrentUser.get();
        FamilyMemberEntity me = familyMemberRepository.findByUserId(userId).orElse(null);
        int[] monthDay = resolveMonthDay(request, null);
        DayRecordEntity record = new DayRecordEntity();
        record.setUserId(userId);
        record.setFamilyId(me == null ? null : me.getFamilyId());
        record.setCreatedAt(LocalDateTime.now());
        applyRequest(record, request, me, monthDay);
        dayRecordRepository.save(record);
        // reminders 仅创建生效,PUT 忽略(改提醒走 /api/days/{id}/reminder)
        List<Integer> reminders = normalizeReminders(request.getReminders());
        if (!reminders.isEmpty()) {
            saveReminder(record.getId(), userId, reminders);
        }
        return toDto(record, userId);
    }

    @Transactional(readOnly = true)
    public DayRecord getDay(Long id) {
        Long userId = CurrentUser.get();
        Long familyId = myFamilyId(userId);
        DayRecordEntity record = getVisibleDay(id, userId, familyId);
        return toDto(record, userId);
    }

    @Transactional
    public DayRecord update(Long id, DayUpsertRequest request) {
        Long userId = CurrentUser.get();
        FamilyMemberEntity me = familyMemberRepository.findByUserId(userId).orElse(null);
        DayRecordEntity record = dayRecordRepository.findById(id)
                .orElseThrow(() -> ApiException.notFound("没有找到这条日子"));
        if (!canEdit(record, userId, me)) {
            throw ApiException.notFound("没有找到这条日子");
        }
        int[] monthDay = resolveMonthDay(request, record);
        applyRequest(record, request, me, monthDay);
        return toDto(dayRecordRepository.save(record), userId);
    }

    @Transactional
    public DeleteDayResponse delete(Long id) {
        Long userId = CurrentUser.get();
        FamilyMemberEntity me = familyMemberRepository.findByUserId(userId).orElse(null);
        DayRecordEntity record = dayRecordRepository.findById(id)
                .orElseThrow(() -> ApiException.notFound("没有找到这条日子"));
        if (!canEdit(record, userId, me)) {
            throw ApiException.notFound("没有找到这条日子");
        }
        // 清全部家人的提醒行:日子不存在则提醒停止(对齐生日簿删除路径)
        dayReminderRepository.deleteByDayId(record.getId());
        dayRecordRepository.delete(record);
        DeleteDayResponse response = new DeleteDayResponse();
        response.setDeleted(true);
        return response;
    }

    @Transactional
    public DayReminder updateReminder(Long id, DayReminderRequest request) {
        Long userId = CurrentUser.get();
        Long familyId = myFamilyId(userId);
        DayRecordEntity record = getVisibleDay(id, userId, familyId);
        if (request == null || request.getReminders() == null) {
            throw ApiException.param("请您选择提醒时间,或清空提醒");
        }
        List<Integer> reminders = normalizeReminders(request.getReminders());
        saveReminder(record.getId(), userId, reminders);
        DayReminder response = new DayReminder();
        response.setDayId(record.getId());
        response.setReminders(reminders);
        return response;
    }

    /** 到期计算:给定日期应提醒清单,返回全量命中项(total 供发送链路频控计算),不做截断 */
    @Transactional(readOnly = true)
    public DayReminderDueResponse getReminderDue(String date) {
        CurrentUser.get();
        LocalDate basis = parseBasisDate(date);
        List<DayReminderDueItem> items = new ArrayList<>();
        for (DayReminderEntity reminder : dayReminderRepository.findByRemindersNot("")) {
            List<Integer> offsets = splitReminders(reminder.getReminders());
            if (offsets.isEmpty()) {
                continue;
            }
            DayRecordEntity record = dayRecordRepository.findById(reminder.getDayId()).orElse(null);
            if (record == null) {
                continue;
            }
            if (record.getKind() == DayKind.TOGETHER) {
                // TOGETHER 无落点:配置当天提醒即每天命中,产出相伴天数
                if (!offsets.contains(0)) {
                    continue;
                }
                long daysSince = Math.max(1, ChronoUnit.DAYS.between(togetherStart(record), basis));
                items.add(dueItem(record, reminder.getUserId(), 0, null, (int) daysSince));
            } else {
                LocalDate next = nextDateOrSkip(record, basis);
                if (next == null) {
                    continue;
                }
                int daysTo = (int) ChronoUnit.DAYS.between(basis, next);
                if (!offsets.contains(daysTo)) {
                    continue;
                }
                items.add(dueItem(record, reminder.getUserId(), daysTo, daysTo, null));
            }
        }
        DayReminderDueResponse response = new DayReminderDueResponse();
        response.setItems(items);
        response.setTotal(items.size());
        return response;
    }

    // ---- 校验与请求落地 ----

    /** 月日解析:非 TOGETHER 必填且由换算核心兜底校验;TOGETHER 缺省取当天(编辑时缺省保留原值) */
    private int[] resolveMonthDay(DayUpsertRequest request, DayRecordEntity editing) {
        validateBasics(request);
        if (request.getKind() == DayKind.TOGETHER) {
            if (request.getCalendarType() != CalendarType.SOLAR) {
                throw ApiException.param("在一起的日子按公历记录");
            }
            if (request.getMonth() == null && request.getDay() == null) {
                if (editing == null) {
                    LocalDate today = LocalDate.now();
                    return new int[] { today.getMonthValue(), today.getDayOfMonth() };
                }
                return new int[] { editing.getMonth(), editing.getDay() };
            }
            if (request.getMonth() == null || request.getDay() == null) {
                throw ApiException.param("请您把日子的月和日选好");
            }
            validateRange(request.getMonth(), request.getDay());
            // 起始年:传 year 用补录年,否则取 createdAt 年(编辑缺省保留原值);
            // 显式传月日时须在起始年真实存在(如 2 月 29 配平年 → 40000),防读取期构造 LocalDate 炸历史行
            int startYear = request.getYear() != null ? request.getYear()
                    : (editing == null ? LocalDate.now().getYear() : editing.getCreatedAt().getYear());
            try {
                LocalDate.of(startYear, request.getMonth(), request.getDay());
            } catch (java.time.DateTimeException e) {
                throw ApiException.param("请您换个日期");
            }
            return new int[] { request.getMonth(), request.getDay() };
        }
        if (request.getMonth() == null || request.getDay() == null) {
            throw ApiException.param("请您把日子的月和日选好");
        }
        if (request.getCalendarType() == CalendarType.LUNAR) {
            if (request.getMonth() < 1 || request.getMonth() > 12
                    || request.getDay() < 1 || request.getDay() > 30) {
                throw ApiException.param("请您换个日期");
            }
        }
        nextDateOrParam(request.getCalendarType(), request.getMonth(), request.getDay());
        return new int[] { request.getMonth(), request.getDay() };
    }

    private void validateBasics(DayUpsertRequest request) {
        if (request == null || request.getKind() == null || request.getCalendarType() == null
                || request.getVisibility() == null) {
            throw ApiException.param("请您把日子信息填写完整");
        }
        if (request.getRelation() == null || request.getRelation().isBlank()
                || request.getRelation().trim().length() > 12) {
            throw ApiException.param("请您把称呼填写完整");
        }
        // year 可空;非空校验 1900..当年(全 kind 统一;BIRTHDAY 仅存档不算年龄)
        if (request.getYear() != null
                && (request.getYear() < 1900 || request.getYear() > LocalDate.now().getYear())) {
            throw ApiException.param("年份要在 1900 年到现在之间");
        }
    }

    private void validateRange(Integer month, Integer day) {
        if (month < 1 || month > 12 || day < 1 || day > 31) {
            throw ApiException.param("请您换个日期");
        }
    }

    private void applyRequest(DayRecordEntity record, DayUpsertRequest request, FamilyMemberEntity me,
                              int[] monthDay) {
        record.setRelation(request.getRelation().trim());
        record.setEmoji(resolveEmoji(request));
        record.setKind(request.getKind());
        record.setCalendarType(request.getKind() == DayKind.TOGETHER
                ? CalendarType.SOLAR : request.getCalendarType());
        record.setMonth(monthDay[0]);
        record.setDay(monthDay[1]);
        // year 全量覆盖:不传即清空(改后周年/相伴字段即时重算)
        record.setYear(request.getYear());
        // 无家族用户传 FAMILY:服务端降级 PRIVATE(有响应可见性可解释,非报错)
        DayVisibility visibility = request.getVisibility();
        if (visibility == DayVisibility.FAMILY && me == null) {
            visibility = DayVisibility.PRIVATE;
        }
        record.setVisibility(visibility);
    }

    private String resolveEmoji(DayUpsertRequest request) {
        if (request.getEmoji() != null && !request.getEmoji().isBlank()) {
            return request.getEmoji().trim();
        }
        return DEFAULT_EMOJIS.getOrDefault(request.getRelation().trim(), "😊");
    }

    private List<Integer> normalizeReminders(List<Integer> values) {
        if (values == null) {
            return List.of();
        }
        List<Integer> copy = new ArrayList<>(values);
        if (copy.size() > 3 || copy.stream().distinct().count() != copy.size()
                || copy.stream().anyMatch(offset -> !ALLOWED_REMINDER_OFFSETS.contains(offset))) {
            throw ApiException.param("提醒时间只能选择提前 7 天、3 天或当天");
        }
        copy.sort(Comparator.reverseOrder());
        return copy;
    }

    // ---- 换算与计算字段 ----

    /** 校验/展示口径:换算核心报参数错误时统一回日子文案(风险5:农历三十无落点等) */
    private LocalDate nextDateOrParam(CalendarType calendarType, Integer month, Integer day) {
        try {
            return CalendarConvertCore.nextDate(calendarType, month, day, false);
        } catch (ApiException e) {
            throw ApiException.param("请您换个日期");
        }
    }

    /** 到期扫描口径:窗口无落点跳过不产出(录入期已拦截,不重复报错) */
    private LocalDate nextDateOrSkip(DayRecordEntity record, LocalDate basis) {
        try {
            return CalendarConvertCore.nextDate(record.getCalendarType(), record.getMonth(), record.getDay(),
                    false, basis);
        } catch (ApiException e) {
            return null;
        }
    }

    private LocalDate parseBasisDate(String date) {
        if (date == null || date.isBlank()) {
            return LocalDate.now();
        }
        try {
            return LocalDate.parse(date);
        } catch (DateTimeParseException e) {
            throw ApiException.param("请您换个日期");
        }
    }

    private LocalDate togetherStart(DayRecordEntity record) {
        // 补录起始年优先(year+month/day);缺省仍 createdAt 年 + month/day(既有数据兼容)
        int startYear = record.getYear() != null ? record.getYear() : record.getCreatedAt().getYear();
        return LocalDate.of(startYear, record.getMonth(), record.getDay());
    }

    private DayRecord toDto(DayRecordEntity record, Long userId) {
        DayRecord dto = new DayRecord();
        dto.setId(record.getId());
        dto.setUserId(record.getUserId());
        dto.setRelation(record.getRelation());
        dto.setEmoji(record.getEmoji());
        dto.setKind(record.getKind());
        dto.setCalendarType(record.getCalendarType());
        dto.setMonth(record.getMonth());
        dto.setDay(record.getDay());
        dto.setVisibility(record.getVisibility());
        dto.setCreatedAt(record.getCreatedAt().toString());
        dto.setReminders(readReminders(record.getId(), userId));
        dto.setYear(record.getYear());
        if (record.getKind() == DayKind.TOGETHER) {
            long togetherDays = Math.max(1, ChronoUnit.DAYS.between(togetherStart(record), LocalDate.now()));
            dto.setTogetherDays((int) togetherDays);
        } else {
            LocalDate next = CalendarConvertCore.nextDate(record.getCalendarType(), record.getMonth(),
                    record.getDay(), false);
            dto.setNextDate(next.toString());
            dto.setNextDateLunarText(CalendarConvertCore.lunarText(next));
            dto.setDaysUntil((int) ChronoUnit.DAYS.between(LocalDate.now(), next));
            // 周年称谓:仅 ANNIVERSARY 且 year 非空输出(N=nextDate 公历年−year);
            // BIRTHDAY 仅存档回显 year,不输出任何年龄派生字段(隐私红线)
            if (record.getKind() == DayKind.ANNIVERSARY && record.getYear() != null) {
                int n = next.getYear() - record.getYear();
                dto.setAnniversaryNumber(n);
                String title = AnniversaryTitleLib.title(n);
                dto.setAnniversaryTitle(title);
                String label = "结婚 " + n + " 周年";
                dto.setAnniversaryLabel(title == null ? label : label + " · " + title);
            }
        }
        return dto;
    }

    private DayReminderDueItem dueItem(DayRecordEntity record, Long userId, int remindOffset,
                                       Integer daysTo, Integer daysSince) {
        DayReminderDueItem item = new DayReminderDueItem();
        item.setDayId(record.getId());
        item.setUserId(userId);
        item.setRemindOffset(remindOffset);
        item.setTitle(DayTextLib.title(record.getKind(), record.getRelation()));
        item.setActionText(DayTextLib.actionText(record.getKind(), remindOffset));
        item.setKind(record.getKind());
        item.setRelation(record.getRelation());
        item.setDaysTo(daysTo);
        item.setDaysSince(daysSince);
        return item;
    }

    // ---- 身份、权限与提醒读写 ----

    private Long myFamilyId(Long userId) {
        return familyMemberRepository.findByUserId(userId)
                .map(FamilyMemberEntity::getFamilyId)
                .orElse(null);
    }

    private DayRecordEntity getVisibleDay(Long id, Long userId, Long familyId) {
        DayRecordEntity record = dayRecordRepository.findById(id)
                .orElseThrow(() -> ApiException.notFound("没有找到这条日子"));
        if (!canView(record, userId, familyId)) {
            throw ApiException.notFound("没有找到这条日子");
        }
        return record;
    }

    private boolean canView(DayRecordEntity record, Long userId, Long familyId) {
        return record.getUserId().equals(userId)
                || (familyId != null && familyId.equals(record.getFamilyId())
                    && record.getVisibility() == DayVisibility.FAMILY);
    }

    private boolean canEdit(DayRecordEntity record, Long userId, FamilyMemberEntity me) {
        return record.getUserId().equals(userId)
                || (me != null && me.getFamilyId().equals(record.getFamilyId())
                    && record.getVisibility() == DayVisibility.FAMILY
                    && me.getRole() == FamilyMemberEntity.Role.OWNER);
    }

    private void saveReminder(Long dayId, Long userId, List<Integer> reminders) {
        DayReminderEntity reminder = dayReminderRepository.findByDayIdAndUserId(dayId, userId)
                .orElseGet(() -> {
                    DayReminderEntity created = new DayReminderEntity();
                    created.setDayId(dayId);
                    created.setUserId(userId);
                    return created;
                });
        reminder.setReminders(joinReminders(reminders));
        dayReminderRepository.save(reminder);
    }

    private List<Integer> readReminders(Long dayId, Long userId) {
        return dayReminderRepository.findByDayIdAndUserId(dayId, userId)
                .map(DayReminderEntity::getReminders)
                .map(this::splitReminders)
                .orElseGet(List::of);
    }

    private List<Integer> splitReminders(String value) {
        if (value == null || value.isBlank()) {
            return List.of();
        }
        return java.util.Arrays.stream(value.split(",")).map(Integer::parseInt).toList();
    }

    private String joinReminders(List<Integer> offsets) {
        return offsets.stream().map(String::valueOf).collect(java.util.stream.Collectors.joining(","));
    }
}
