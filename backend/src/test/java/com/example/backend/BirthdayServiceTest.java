package com.example.backend;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

import com.example.backend.common.ApiException;
import com.example.backend.common.CurrentUser;
import com.example.backend.dto.BirthdayDtos.BirthdayReminderRequest;
import com.example.backend.dto.BirthdayDtos.BirthdayUpsertRequest;
import com.example.backend.dto.BirthdayDtos.CalendarConvertRequest;
import com.example.backend.dto.BirthdayDtos.CalendarType;
import com.example.backend.dto.BirthdayDtos.SubjectType;
import com.example.backend.dto.BirthdayDtos.Visibility;
import com.example.backend.entity.BirthdayRecordEntity;
import com.example.backend.entity.FamilyMemberEntity;
import com.example.backend.repository.BirthdayRecordRepository;
import com.example.backend.repository.BirthdayReminderRepository;
import com.example.backend.repository.FamilyMemberRepository;
import com.example.backend.service.BirthdayService;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class BirthdayServiceTest {

    private static final Long ME = 1L;
    private static final Long FAMILY = 10L;

    @Mock private BirthdayRecordRepository birthdayRecordRepository;
    @Mock private BirthdayReminderRepository birthdayReminderRepository;
    @Mock private FamilyMemberRepository familyMemberRepository;

    private BirthdayService service;

    @BeforeEach
    void setup() {
        service = new BirthdayService(birthdayRecordRepository, birthdayReminderRepository, familyMemberRepository);
        CurrentUser.set(ME);
        FamilyMemberEntity member = new FamilyMemberEntity();
        member.setUserId(ME);
        member.setFamilyId(FAMILY);
        member.setRole(FamilyMemberEntity.Role.OWNER);
        lenient().when(familyMemberRepository.findByUserId(ME)).thenReturn(Optional.of(member));
        lenient().when(birthdayRecordRepository.findByFamilyIdOrderByIdDesc(FAMILY)).thenReturn(List.of());
        lenient().when(birthdayReminderRepository.findByBirthdayIdAndUserId(any(), eq(ME))).thenReturn(Optional.empty());
    }

    @AfterEach
    void cleanup() { CurrentUser.clear(); }

    @Test
    void 公历日期换算返回农历信息() {
        CalendarConvertRequest request = new CalendarConvertRequest();
        request.setCalendarType(CalendarType.SOLAR);
        request.setSolarDate("2026-02-17");

        var response = service.convert(request);
        assertEquals("2026-02-17", response.getSolarDate());
        assertFalse(response.getLunarText().isBlank());
        assertEquals(1, response.getLunarMonth());
    }

    @Test
    void 未来的只查日期被拒绝() {
        CalendarConvertRequest request = new CalendarConvertRequest();
        request.setCalendarType(CalendarType.SOLAR);
        request.setSolarDate("2099-01-01");

        ApiException exception = assertThrows(ApiException.class, () -> service.convert(request));
        assertEquals("40000", exception.getCode());
    }

    @Test
    void 无效提醒时点被拒绝() {
        BirthdayRecordEntity record = record(20L, Visibility.FAMILY, ME);
        when(birthdayRecordRepository.findById(20L)).thenReturn(Optional.of(record));
        BirthdayReminderRequest request = new BirthdayReminderRequest();
        request.setReminderDays(List.of(1));

        ApiException exception = assertThrows(ApiException.class, () -> service.updateReminder(20L, request));
        assertEquals("40000", exception.getCode());
    }

    @Test
    void 平年二月二十九生日按二月二十八过() {
        CalendarConvertRequest request = new CalendarConvertRequest();
        request.setCalendarType(CalendarType.SOLAR);
        request.setMonth(2);
        request.setDay(29);

        var response = service.convert(request);
        String date = response.getSolarDate();
        org.junit.jupiter.api.Assertions.assertTrue(date.endsWith("-02-28") || date.endsWith("-02-29"), date);
    }

    @Test
    void 窗口内无闰月时按平常月过() {
        CalendarConvertRequest request = new CalendarConvertRequest();
        request.setCalendarType(CalendarType.LUNAR);
        request.setMonth(6);
        request.setDay(1);
        request.setIsLeapMonth(true);

        var response = service.convert(request);
        assertFalse(response.getSolarDate().isBlank());
    }

    @Test
    void 私密生日不展示给其他家人() {
        BirthdayRecordEntity record = record(20L, Visibility.PRIVATE, 2L);
        when(birthdayRecordRepository.findByFamilyIdOrderByIdDesc(FAMILY)).thenReturn(List.of(record));

        assertEquals(0, service.getBirthdays().getItems().size());
    }

    private BirthdayRecordEntity record(Long id, Visibility visibility, Long createdBy) {
        BirthdayRecordEntity record = new BirthdayRecordEntity();
        record.setId(id);
        record.setFamilyId(FAMILY);
        record.setCreatedBy(createdBy);
        record.setDisplayName("妈妈");
        record.setSubjectType(SubjectType.CONTACT);
        record.setCalendarType(CalendarType.SOLAR);
        record.setMonth(12);
        record.setDay(31);
        record.setLeapMonth(false);
        record.setVisibility(visibility);
        return record;
    }
}
