package com.example.backend;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.backend.common.ApiException;
import com.example.backend.common.CurrentUser;
import com.example.backend.dto.BaguaDtos.BaguaCelebrateResponse;
import com.example.backend.dto.BaguaDtos.BaguaStatusResponse;
import com.example.backend.dto.BaguaDtos.BaguaTrigramProgress;
import com.example.backend.dto.FortuneDtos.Gua;
import com.example.backend.entity.BlessEventEntity;
import com.example.backend.entity.FamilyMemberEntity;
import com.example.backend.entity.FamilyMilestoneEntity;
import com.example.backend.entity.FortuneDrawRecordEntity;
import com.example.backend.repository.BlessEventRepository;
import com.example.backend.repository.FamilyMemberRepository;
import com.example.backend.repository.FamilyMilestoneRepository;
import com.example.backend.repository.FortuneDrawRecordRepository;
import com.example.backend.service.BaguaService;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/** 八卦集福阵进度口径测试:乾兑离震巽坎艮坤按 design v1.5 §1.3 表格推导,庆祝幂等 */
@ExtendWith(MockitoExtension.class)
class BaguaServiceTest {

    @Mock
    private FamilyMemberRepository memberRepository;
    @Mock
    private FortuneDrawRecordRepository drawRepository;
    @Mock
    private BlessEventRepository blessEventRepository;
    @Mock
    private FamilyMilestoneRepository milestoneRepository;

    private BaguaService service;

    private static final Long ME = 1L;
    private static final Long FAMILY = 10L;

    @BeforeEach
    void setup() {
        service = new BaguaService(memberRepository, drawRepository, blessEventRepository, milestoneRepository);
        CurrentUser.set(ME);
    }

    @AfterEach
    void cleanup() {
        CurrentUser.clear();
    }

    private FamilyMemberEntity member(Long userId, int streakDays) {
        FamilyMemberEntity m = new FamilyMemberEntity();
        m.setUserId(userId);
        m.setFamilyId(FAMILY);
        m.setStreakDays(streakDays);
        return m;
    }

    private FortuneDrawRecordEntity draw(Long userId, LocalDate date, String taskItem) {
        FortuneDrawRecordEntity r = new FortuneDrawRecordEntity();
        r.setUserId(userId);
        r.setFamilyId(FAMILY);
        r.setDate(date);
        r.setTaskItem(taskItem);
        return r;
    }

    /** 全亮样本:3 人家族,近 30 天每天全员抽卡,离/坎/巽按人分工,艮/兑/坤达标 */
    private void setupFullLit() {
        LocalDate today = LocalDate.now();
        List<FamilyMemberEntity> members = List.of(member(1L, 30), member(2L, 5), member(3L, 7));
        lenient().when(memberRepository.findByUserId(ME)).thenReturn(Optional.of(members.get(0)));
        lenient().when(memberRepository.findByFamilyIdOrderByIdAsc(FAMILY)).thenReturn(members);
        lenient().when(blessEventRepository.countByFamilyIdAndType(FAMILY, BlessEventEntity.Type.SHARE_CARD))
                .thenReturn(30L);

        List<FortuneDrawRecordEntity> records = new ArrayList<>();
        for (int i = 0; i < 30; i++) {
            LocalDate day = today.minusDays(i);
            records.add(draw(1L, day, "宜晚上十点前早睡，养足精神"));   // 离:早睡
            records.add(draw(2L, day, "宜勤喝水，小口慢饮"));           // 坎:喝水
            records.add(draw(3L, day, i < 10 ? "宜给家人打电话说说近况" : "宜散步二十分钟")); // 巽:打电话×10
        }
        // 坤:节气日(离线表内固定日)完成一件今日宜
        records.add(draw(3L, LocalDate.of(2026, 1, 5), "宜晒十分钟太阳"));
        lenient().when(drawRepository.findByFamilyId(FAMILY)).thenReturn(records);
        lenient().when(milestoneRepository.existsByFamilyIdAndMilestone(FAMILY, FamilyMilestoneEntity.BAGUA_COMPLETE))
                .thenReturn(false);
    }

    @Test
    void 八卦推导口径与设计表格一致() {
        setupFullLit();
        BaguaStatusResponse status = service.getStatus();

        assertEquals(8, status.getTrigrams().size());
        // 固定顺序:乾兑离震巽坎艮坤
        Gua[] order = {Gua.QIAN, Gua.DUI, Gua.LI, Gua.ZHEN, Gua.XUN, Gua.KAN, Gua.GEN, Gua.KUN};
        for (int i = 0; i < 8; i++) {
            assertEquals(order[i], status.getTrigrams().get(i).getGua());
            assertTrue(status.getTrigrams().get(i).isLit(), order[i] + " 应点亮");
        }
        // 口径断言:乾=全员连续天数 / 离=去重人日 / 巽=计次 / 艮=个人连续最大值
        assertEquals(30, status.getTrigrams().get(0).getCurrent());  // 乾:近 30 天全员抽卡
        assertEquals(30, status.getTrigrams().get(1).getCurrent());  // 兑:分享 30 次
        assertEquals(30, status.getTrigrams().get(2).getCurrent());  // 离:早睡 30 人日
        assertEquals(2, status.getTrigrams().get(3).getCurrent());   // 震:3 人 → memberCount-1
        assertEquals(10, status.getTrigrams().get(4).getCurrent());  // 巽:打电话 10 次
        assertEquals(30, status.getTrigrams().get(5).getCurrent());  // 坎:喝水 30 人日
        assertEquals(30, status.getTrigrams().get(6).getCurrent());  // 艮:个人连续最大 30
        // 坤:节气日完成今日宜(若当天恰为节气,近 30 天窗口内任务也计入,故只断言下限)
        assertTrue(status.getTrigrams().get(7).getCurrent() >= 1, "坤:节气日至少完成 1 件");

        assertEquals(8, status.getLitCount());
        assertTrue(status.isComplete());
        assertFalse(status.isCelebrated());
        for (BaguaTrigramProgress p : status.getTrigrams()) {
            assertTrue(p.getHint() != null && p.getHint().length() <= 24, "提示文案应 ≤24 字");
        }
    }

    @Test
    void 乾连续天数中断即止() {
        LocalDate today = LocalDate.now();
        List<FamilyMemberEntity> members = List.of(member(1L, 1), member(2L, 1));
        when(memberRepository.findByUserId(ME)).thenReturn(Optional.of(members.get(0)));
        when(memberRepository.findByFamilyIdOrderByIdAsc(FAMILY)).thenReturn(members);
        when(blessEventRepository.countByFamilyIdAndType(FAMILY, BlessEventEntity.Type.SHARE_CARD))
                .thenReturn(0L);
        // 今日全员已抽,昨日只有 1 人抽 → 乾 current=1
        when(drawRepository.findByFamilyId(FAMILY)).thenReturn(List.of(
                draw(1L, today, null),
                draw(2L, today, null),
                draw(1L, today.minusDays(1), null)));

        BaguaStatusResponse status = service.getStatus();
        assertEquals(1, status.getTrigrams().get(0).getCurrent());
        assertFalse(status.getTrigrams().get(0).isLit());
    }

    @Test
    void 单人家庭震不亮() {
        LocalDate today = LocalDate.now();
        List<FamilyMemberEntity> members = List.of(member(1L, 1));
        when(memberRepository.findByUserId(ME)).thenReturn(Optional.of(members.get(0)));
        when(memberRepository.findByFamilyIdOrderByIdAsc(FAMILY)).thenReturn(members);
        when(blessEventRepository.countByFamilyIdAndType(FAMILY, BlessEventEntity.Type.SHARE_CARD))
                .thenReturn(0L);
        when(drawRepository.findByFamilyId(FAMILY)).thenReturn(List.of(draw(1L, today, null)));

        BaguaStatusResponse status = service.getStatus();
        BaguaTrigramProgress zhen = status.getTrigrams().get(3);
        assertEquals(0, zhen.getCurrent());
        assertFalse(zhen.isLit());
        assertFalse(status.isComplete());
    }

    @Test
    void 未圆满时庆祝返回40900() {
        setupFullLitButEmpty();
        ApiException e = assertThrows(ApiException.class, () -> service.celebrate());
        assertEquals("40900", e.getCode());
        verify(milestoneRepository, never()).save(any());
    }

    /** 空进度样本:无任何抽签记录,8 卦全灭 */
    private void setupFullLitButEmpty() {
        List<FamilyMemberEntity> members = List.of(member(1L, 0));
        when(memberRepository.findByUserId(ME)).thenReturn(Optional.of(members.get(0)));
        when(memberRepository.findByFamilyIdOrderByIdAsc(FAMILY)).thenReturn(members);
        when(blessEventRepository.countByFamilyIdAndType(FAMILY, BlessEventEntity.Type.SHARE_CARD))
                .thenReturn(0L);
        when(drawRepository.findByFamilyId(FAMILY)).thenReturn(List.of());
    }

    @Test
    void 圆满庆祝幂等落里程碑() {
        setupFullLit();
        // 第一次:未标记 → 落库
        when(milestoneRepository.existsByFamilyIdAndMilestone(FAMILY, FamilyMilestoneEntity.BAGUA_COMPLETE))
                .thenReturn(false);
        BaguaCelebrateResponse first = service.celebrate();
        assertTrue(first.isCelebrated());
        verify(milestoneRepository, times(1)).save(any());

        // 第二次:已标记 → 幂等返回,不重复落库
        when(milestoneRepository.existsByFamilyIdAndMilestone(FAMILY, FamilyMilestoneEntity.BAGUA_COMPLETE))
                .thenReturn(true);
        BaguaCelebrateResponse second = service.celebrate();
        assertTrue(second.isCelebrated());
        verify(milestoneRepository, times(1)).save(any()); // 仍是 1 次
    }

    @Test
    void 未加入家族返回40900() {
        when(memberRepository.findByUserId(ME)).thenReturn(Optional.empty());
        ApiException e = assertThrows(ApiException.class, () -> service.getStatus());
        assertEquals("40900", e.getCode());
    }
}
