package com.example.backend;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.backend.common.ApiException;
import com.example.backend.common.CurrentUser;
import com.example.backend.dto.FortuneDtos;
import com.example.backend.entity.DailyFortuneCardEntity;
import com.example.backend.entity.FamilyMemberEntity;
import com.example.backend.entity.FortuneDrawRecordEntity;
import com.example.backend.repository.DailyFortuneCardRepository;
import com.example.backend.repository.FamilyMemberRepository;
import com.example.backend.repository.FamilyRepository;
import com.example.backend.repository.FortuneDrawRecordRepository;
import com.example.backend.service.BlessService;
import com.example.backend.service.FortuneService;
import java.time.LocalDate;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/** v1.5 任务可选体测试:taskItem 须属于当日卡面 yiItems,缺省兼容旧行为(不写 task_item) */
@ExtendWith(MockitoExtension.class)
class FortuneServiceTaskDoneTest {

    @Mock
    private DailyFortuneCardRepository cardRepository;
    @Mock
    private FortuneDrawRecordRepository drawRepository;
    @Mock
    private FamilyMemberRepository memberRepository;
    @Mock
    private FamilyRepository familyRepository;
    @Mock
    private BlessService blessService;

    private FortuneService service;

    private static final Long ME = 1L;
    private static final Long FAMILY = 10L;
    private static final LocalDate TODAY = LocalDate.now();

    @BeforeEach
    void setup() {
        service = new FortuneService(cardRepository, drawRepository, memberRepository,
                familyRepository, blessService);
        CurrentUser.set(ME);

        FamilyMemberEntity member = new FamilyMemberEntity();
        member.setUserId(ME);
        member.setFamilyId(FAMILY);
        lenient().when(memberRepository.findByUserId(ME)).thenReturn(Optional.of(member));

        FortuneDrawRecordEntity record = new FortuneDrawRecordEntity();
        record.setUserId(ME);
        record.setFamilyId(FAMILY);
        record.setDate(TODAY);
        lenient().when(drawRepository.findByUserIdAndDate(ME, TODAY)).thenReturn(Optional.of(record));

        DailyFortuneCardEntity card = new DailyFortuneCardEntity();
        card.setFamilyId(FAMILY);
        card.setDate(TODAY);
        card.setYiItems("宜晚上十点前早睡，养足精神,宜勤喝水，小口慢饮");
        lenient().when(cardRepository.findByFamilyIdAndDate(FAMILY, TODAY)).thenReturn(Optional.of(card));
    }

    @AfterEach
    void cleanup() {
        CurrentUser.clear();
    }

    private FortuneDtos.FortuneTaskDoneRequest request(String taskItem) {
        FortuneDtos.FortuneTaskDoneRequest req = new FortuneDtos.FortuneTaskDoneRequest();
        req.setTaskItem(taskItem);
        return req;
    }

    @Test
    void 事项不属于当日宜返回40000() {
        ApiException e = assertThrows(ApiException.class,
                () -> service.completeFortuneTask(request("宜跳绳一百下")));
        assertEquals("40000", e.getCode());
    }

    @Test
    void 合法事项写入task_item并发放15福值() {
        when(blessService.grant(eq(ME), eq(FAMILY), any(), eq(null)))
                .thenReturn(new BlessService.GrantResult(15, 100, 500, FortuneDtos.PlateLevel.BRONZE));

        FortuneDtos.BlessResult resp = service.completeFortuneTask(request("宜勤喝水，小口慢饮"));
        assertEquals(15, resp.getAmount());

        ArgumentCaptor<FortuneDrawRecordEntity> captor =
                ArgumentCaptor.forClass(FortuneDrawRecordEntity.class);
        verify(drawRepository).save(captor.capture());
        assertEquals("宜勤喝水，小口慢饮", captor.getValue().getTaskItem());
        assertEquals(true, captor.getValue().isTaskDone());
    }

    @Test
    void 缺省事项兼容旧行为不写task_item() {
        when(blessService.grant(eq(ME), eq(FAMILY), any(), eq(null)))
                .thenReturn(new BlessService.GrantResult(15, 100, 500, FortuneDtos.PlateLevel.BRONZE));

        service.completeFortuneTask(null);
        ArgumentCaptor<FortuneDrawRecordEntity> captor =
                ArgumentCaptor.forClass(FortuneDrawRecordEntity.class);
        verify(drawRepository).save(captor.capture());
        assertNull(captor.getValue().getTaskItem());
        assertEquals(true, captor.getValue().isTaskDone());
    }

    @Test
    void 当日已完成返回40900() {
        FortuneDrawRecordEntity done = new FortuneDrawRecordEntity();
        done.setUserId(ME);
        done.setFamilyId(FAMILY);
        done.setDate(TODAY);
        done.setTaskDone(true);
        when(drawRepository.findByUserIdAndDate(ME, TODAY)).thenReturn(Optional.of(done));

        ApiException e = assertThrows(ApiException.class,
                () -> service.completeFortuneTask(request("宜勤喝水，小口慢饮")));
        assertEquals("40900", e.getCode());
    }
}
