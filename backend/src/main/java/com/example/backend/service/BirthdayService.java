package com.example.backend.service;

import com.example.backend.common.ApiException;
import com.example.backend.common.CurrentUser;
import com.example.backend.dto.BirthdayDtos;
import com.example.backend.dto.BirthdayDtos.BirthdayListResponse;
import com.example.backend.dto.BirthdayDtos.BirthdayRecord;
import com.example.backend.dto.BirthdayDtos.BirthdayReminder;
import com.example.backend.dto.BirthdayDtos.BirthdayReminderRequest;
import com.example.backend.dto.BirthdayDtos.BirthdayUpsertRequest;
import com.example.backend.dto.BirthdayDtos.CalendarConvertRequest;
import com.example.backend.dto.BirthdayDtos.CalendarConvertResponse;
import com.example.backend.dto.BirthdayDtos.CalendarType;
import com.example.backend.dto.BirthdayDtos.DeleteBirthdayResponse;
import com.example.backend.dto.BirthdayDtos.SubjectType;
import com.example.backend.dto.BirthdayDtos.UpcomingBirthdayResponse;
import com.example.backend.dto.BirthdayDtos.Visibility;
import com.example.backend.entity.BirthdayRecordEntity;
import com.example.backend.entity.BirthdayReminderEntity;
import com.example.backend.entity.FamilyMemberEntity;
import com.example.backend.repository.BirthdayRecordRepository;
import com.example.backend.repository.BirthdayReminderRepository;
import com.example.backend.repository.FamilyMemberRepository;
import net.time4j.PlainDate;
import net.time4j.calendar.ChineseCalendar;
import net.time4j.calendar.EastAsianMonth;
import net.time4j.calendar.EastAsianYear;
import net.time4j.engine.EpochDays;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 家庭生日簿：只持久化生日月、日和日期口径；公农历换算均由服务端完成。 */
@Service
public class BirthdayService {

    private static final int CONTACT_LIMIT = 20;
    private static final List<Integer> ALLOWED_REMINDER_DAYS = List.of(7, 3, 0);

    private final BirthdayRecordRepository birthdayRecordRepository;
    private final BirthdayReminderRepository birthdayReminderRepository;
    private final FamilyMemberRepository familyMemberRepository;

    public BirthdayService(BirthdayRecordRepository birthdayRecordRepository,
                           BirthdayReminderRepository birthdayReminderRepository,
                           FamilyMemberRepository familyMemberRepository) {
        this.birthdayRecordRepository = birthdayRecordRepository;
        this.birthdayReminderRepository = birthdayReminderRepository;
        this.familyMemberRepository = familyMemberRepository;
    }

    @Transactional(readOnly = true)
    public CalendarConvertResponse convert(CalendarConvertRequest request) {
        if (request == null || request.getCalendarType() == null) {
            throw ApiException.param("请选择日期口径");
        }
        if (request.getCalendarType() == CalendarType.SOLAR && hasText(request.getSolarDate())) {
            LocalDate date = parseHistoricalSolarDate(request.getSolarDate());
            return conversionOf(date);
        }
        LocalDate nextDate = nextDate(request.getCalendarType(), request.getMonth(), request.getDay(),
                Boolean.TRUE.equals(request.getIsLeapMonth()));
        return conversionOf(nextDate);
    }

    @Transactional(readOnly = true)
    public BirthdayListResponse getBirthdays() {
        Long userId = CurrentUser.get();
        FamilyMemberEntity me = requireMember(userId);
        List<BirthdayRecord> items = birthdayRecordRepository.findByFamilyIdOrderByIdDesc(me.getFamilyId()).stream()
                .filter(record -> canView(record, userId))
                .map(record -> toDto(record, userId))
                .sorted(Comparator.comparingLong(BirthdayRecord::getDaysUntil))
                .toList();
        BirthdayListResponse response = new BirthdayListResponse();
        response.setItems(items);
        return response;
    }

    @Transactional(readOnly = true)
    public UpcomingBirthdayResponse getUpcomingBirthday() {
        BirthdayListResponse list = getBirthdays();
        UpcomingBirthdayResponse response = new UpcomingBirthdayResponse();
        response.setUpcoming(list.getItems().isEmpty() ? null : list.getItems().getFirst());
        return response;
    }

    @Transactional
    public BirthdayRecord create(BirthdayUpsertRequest request) {
        Long userId = CurrentUser.get();
        FamilyMemberEntity me = requireMember(userId);
        validateRequest(request, me.getFamilyId(), userId, null);
        if (request.getSubjectType() == SubjectType.CONTACT
                && birthdayRecordRepository.countByFamilyIdAndCreatedByAndSubjectType(
                        me.getFamilyId(), userId, SubjectType.CONTACT) >= CONTACT_LIMIT) {
            throw ApiException.conflict("最多可以记下 20 位亲友的生日");
        }
        if (request.getSubjectType() == SubjectType.MEMBER && hasMemberBirthday(me.getFamilyId(), request.getSubjectUserId())) {
            throw ApiException.conflict("这位家人的生日已经记下了，您可以直接修改");
        }
        BirthdayRecordEntity record = new BirthdayRecordEntity();
        record.setFamilyId(me.getFamilyId());
        record.setCreatedBy(userId);
        applyRequest(record, request);
        return toDto(birthdayRecordRepository.save(record), userId);
    }

    @Transactional
    public BirthdayRecord update(Long id, BirthdayUpsertRequest request) {
        Long userId = CurrentUser.get();
        FamilyMemberEntity me = requireMember(userId);
        BirthdayRecordEntity record = getRecordInFamily(id, me.getFamilyId());
        if (!canEdit(record, userId, me)) {
            throw ApiException.notFound("没有找到这条生日记录");
        }
        validateRequest(request, me.getFamilyId(), userId, record.getId());
        if (request.getSubjectType() == SubjectType.MEMBER
                && (!request.getSubjectUserId().equals(record.getSubjectUserId()) || record.getSubjectType() != SubjectType.MEMBER)
                && hasMemberBirthday(me.getFamilyId(), request.getSubjectUserId())) {
            throw ApiException.conflict("这位家人的生日已经记下了，您可以直接修改");
        }
        applyRequest(record, request);
        return toDto(birthdayRecordRepository.save(record), userId);
    }

    @Transactional
    public DeleteBirthdayResponse delete(Long id) {
        Long userId = CurrentUser.get();
        FamilyMemberEntity me = requireMember(userId);
        BirthdayRecordEntity record = getRecordInFamily(id, me.getFamilyId());
        if (!canEdit(record, userId, me)) {
            throw ApiException.notFound("没有找到这条生日记录");
        }
        birthdayReminderRepository.deleteByBirthdayId(record.getId());
        birthdayRecordRepository.delete(record);
        DeleteBirthdayResponse response = new DeleteBirthdayResponse();
        response.setDeleted(true);
        return response;
    }

    @Transactional
    public BirthdayReminder updateReminder(Long id, BirthdayReminderRequest request) {
        Long userId = CurrentUser.get();
        FamilyMemberEntity me = requireMember(userId);
        BirthdayRecordEntity record = getRecordInFamily(id, me.getFamilyId());
        if (!canView(record, userId)) {
            throw ApiException.notFound("没有找到这条生日记录");
        }
        List<Integer> reminderDays = normalizeReminderDays(request);
        BirthdayReminderEntity reminder = birthdayReminderRepository.findByBirthdayIdAndUserId(id, userId)
                .orElseGet(() -> {
                    BirthdayReminderEntity created = new BirthdayReminderEntity();
                    created.setBirthdayId(id);
                    created.setUserId(userId);
                    return created;
                });
        reminder.setReminderDays(joinReminderDays(reminderDays));
        birthdayReminderRepository.save(reminder);
        BirthdayReminder response = new BirthdayReminder();
        response.setBirthdayId(id);
        response.setReminderDays(reminderDays);
        return response;
    }

    private FamilyMemberEntity requireMember(Long userId) {
        return familyMemberRepository.findByUserId(userId)
                .orElseThrow(() -> ApiException.conflict("请您先加入一个家，再记下生日"));
    }

    private BirthdayRecordEntity getRecordInFamily(Long id, Long familyId) {
        return birthdayRecordRepository.findById(id)
                .filter(record -> record.getFamilyId().equals(familyId))
                .orElseThrow(() -> ApiException.notFound("没有找到这条生日记录"));
    }

    private boolean canView(BirthdayRecordEntity record, Long userId) {
        return record.getVisibility() == Visibility.FAMILY || record.getCreatedBy().equals(userId);
    }

    private boolean canEdit(BirthdayRecordEntity record, Long userId, FamilyMemberEntity me) {
        return record.getCreatedBy().equals(userId)
                || (record.getVisibility() == Visibility.FAMILY && me.getRole() == FamilyMemberEntity.Role.OWNER);
    }

    private boolean hasMemberBirthday(Long familyId, Long subjectUserId) {
        return birthdayRecordRepository.findByFamilyIdOrderByIdDesc(familyId).stream()
                .anyMatch(record -> record.getSubjectType() == SubjectType.MEMBER && subjectUserId.equals(record.getSubjectUserId()));
    }

    private void validateRequest(BirthdayUpsertRequest request, Long familyId, Long userId, Long editingId) {
        if (request == null || request.getSubjectType() == null || request.getCalendarType() == null
                || request.getVisibility() == null || !hasText(request.getDisplayName())
                || request.getMonth() == null || request.getDay() == null || request.getIsLeapMonth() == null) {
            throw ApiException.param("请您把生日信息填写完整");
        }
        if (request.getDisplayName().trim().length() > 12) {
            throw ApiException.param("家人的称呼最多 12 个字");
        }
        if (request.getSubjectType() == SubjectType.MEMBER) {
            if (request.getSubjectUserId() == null) {
                throw ApiException.param("请选择一位家人");
            }
            FamilyMemberEntity subject = familyMemberRepository.findByUserId(request.getSubjectUserId())
                    .filter(member -> member.getFamilyId().equals(familyId))
                    .orElseThrow(() -> ApiException.param("请选择同一个家里的家人"));
        } else if (request.getSubjectUserId() != null) {
            throw ApiException.param("亲友生日不需要选择家族成员");
        }
        if (request.getCalendarType() == CalendarType.SOLAR && Boolean.TRUE.equals(request.getIsLeapMonth())) {
            throw ApiException.param("公历生日不能选择闰月");
        }
        nextDate(request.getCalendarType(), request.getMonth(), request.getDay(), Boolean.TRUE.equals(request.getIsLeapMonth()));
    }

    private void applyRequest(BirthdayRecordEntity record, BirthdayUpsertRequest request) {
        record.setSubjectType(request.getSubjectType());
        record.setSubjectUserId(request.getSubjectType() == SubjectType.MEMBER ? request.getSubjectUserId() : null);
        record.setDisplayName(request.getDisplayName().trim());
        record.setCalendarType(request.getCalendarType());
        record.setMonth(request.getMonth());
        record.setDay(request.getDay());
        record.setLeapMonth(Boolean.TRUE.equals(request.getIsLeapMonth()));
        record.setVisibility(request.getVisibility());
    }

    private BirthdayRecord toDto(BirthdayRecordEntity record, Long userId) {
        LocalDate next = nextDate(record.getCalendarType(), record.getMonth(), record.getDay(), record.isLeapMonth());
        BirthdayRecord dto = new BirthdayRecord();
        dto.setId(record.getId());
        dto.setFamilyId(record.getFamilyId());
        dto.setSubjectType(record.getSubjectType());
        dto.setSubjectUserId(record.getSubjectUserId());
        dto.setDisplayName(record.getDisplayName());
        dto.setCalendarType(record.getCalendarType());
        dto.setMonth(record.getMonth());
        dto.setDay(record.getDay());
        dto.setLeapMonth(record.isLeapMonth());
        dto.setVisibility(record.getVisibility());
        dto.setCreatedBy(record.getCreatedBy());
        dto.setNextBirthdayDate(next.toString());
        dto.setNextBirthdayLunarText(lunarText(next));
        dto.setDaysUntil(ChronoUnit.DAYS.between(LocalDate.now(), next));
        dto.setReminderDays(readReminderDays(record.getId(), userId));
        return dto;
    }

    private CalendarConvertResponse conversionOf(LocalDate date) {
        ChineseCalendar lunar = chineseDate(date);
        CalendarConvertResponse response = new CalendarConvertResponse();
        response.setSolarDate(date.toString());
        response.setLunarText(lunarText(lunar));
        response.setLunarMonth(lunar.getMonth().getNumber());
        response.setLunarDay(lunar.getDayOfMonth());
        response.setLeapMonth(lunar.getMonth().isLeap());
        return response;
    }

    private LocalDate nextDate(CalendarType calendarType, Integer month, Integer day, boolean isLeapMonth) {
        if (month == null || day == null) {
            throw ApiException.param("请选择生日的月和日");
        }
        LocalDate today = LocalDate.now();
        if (calendarType == CalendarType.SOLAR) {
            LocalDate candidate = solarCandidate(today.getYear(), month, day);
            return candidate.isBefore(today) ? candidate.plusYears(1) : candidate;
        }
        // 闰月生日：优先找窗口年份内真正的闰月；都没有时按平常月同日过
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

    private LocalDate solarCandidate(int year, Integer month, Integer day) {
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

    /** 某农历年该月日的公历日期；该年无此闰月或日期不存在时返回 null */
    private LocalDate lunarCandidate(int year, Integer month, Integer day, boolean isLeapMonth) {
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

    private LocalDate parseHistoricalSolarDate(String value) {
        try {
            LocalDate date = LocalDate.parse(value);
            if (date.isBefore(LocalDate.of(1900, 1, 1)) || date.isAfter(LocalDate.now())) {
                throw ApiException.param("请选择 1900 年至今天的日期");
            }
            return date;
        } catch (java.time.format.DateTimeParseException e) {
            throw ApiException.param("请选择正确的公历日期");
        }
    }

    private String lunarText(LocalDate date) {
        return lunarText(chineseDate(date));
    }

    private String lunarText(ChineseCalendar lunar) {
        String prefix = lunar.getMonth().isLeap() ? "闰" : "";
        return "农历" + prefix + lunar.getMonth().getDisplayName(java.util.Locale.SIMPLIFIED_CHINESE,
                net.time4j.format.NumberSystem.ARABIC) + "月" + lunar.getDayOfMonth() + "日";
    }

    private ChineseCalendar chineseDate(LocalDate date) {
        return ChineseCalendar.axis().getCalendarSystem().transform(
                PlainDate.from(date).getDaysSinceEpochUTC());
    }

    private LocalDate fromChineseDate(ChineseCalendar lunar) {
        long utcDays = lunar.getDaysSinceEpochUTC();
        return PlainDate.of(utcDays, EpochDays.UTC).toTemporalAccessor();
    }

    private List<Integer> normalizeReminderDays(BirthdayReminderRequest request) {
        if (request == null || request.getReminderDays() == null) {
            throw ApiException.param("请选择提醒时间，或清空全部提醒");
        }
        List<Integer> values = new ArrayList<>(request.getReminderDays());
        if (values.size() > 3 || values.stream().distinct().count() != values.size()
                || values.stream().anyMatch(day -> !ALLOWED_REMINDER_DAYS.contains(day))) {
            throw ApiException.param("提醒时间只能选择提前 7 天、3 天或当天");
        }
        values.sort(Comparator.reverseOrder());
        return values;
    }

    private List<Integer> readReminderDays(Long birthdayId, Long userId) {
        return birthdayReminderRepository.findByBirthdayIdAndUserId(birthdayId, userId)
                .map(BirthdayReminderEntity::getReminderDays)
                .map(this::splitReminderDays)
                .orElseGet(List::of);
    }

    private String joinReminderDays(List<Integer> days) {
        return days.stream().map(String::valueOf).collect(java.util.stream.Collectors.joining(","));
    }

    private List<Integer> splitReminderDays(String value) {
        if (!hasText(value)) {
            return List.of();
        }
        return java.util.Arrays.stream(value.split(",")).map(Integer::parseInt).toList();
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
