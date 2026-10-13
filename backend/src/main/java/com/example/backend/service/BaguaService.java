package com.example.backend.service;

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
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 八卦集福阵:8 卦进度纯服务端推导,读接口无副作用(不落进度表,每次现算,MVP 数据量无压力)。
 * 进度口径(design v1.5 §1.3):
 * 乾=连续 7 天全员抽卡 / 兑=分享福签 30 次 / 离=早睡 30 去重(人,日) / 震=家中 2 人以上 /
 * 巽=打电话 10 次 / 坎=喝水 30 去重(人,日) / 艮=个人连续抽卡 30 天 / 坤=节气日完成今日宜。
 * lit = current >= target,只涨不跌;圆满庆祝由 POST /api/bagua/celebrate 幂等落 family_milestone。
 */
@Service
public class BaguaService {

    /** 节气日期离线表(2026-2030,北京时间;每年 12 月对照万年历滚动补下一年) */
    private static final Set<LocalDate> SOLAR_TERMS = Set.of(
            LocalDate.of(2026, 1, 5), LocalDate.of(2026, 1, 20), LocalDate.of(2026, 2, 4),
            LocalDate.of(2026, 2, 18), LocalDate.of(2026, 3, 5), LocalDate.of(2026, 3, 20),
            LocalDate.of(2026, 4, 5), LocalDate.of(2026, 4, 20), LocalDate.of(2026, 5, 5),
            LocalDate.of(2026, 5, 21), LocalDate.of(2026, 6, 5), LocalDate.of(2026, 6, 21),
            LocalDate.of(2026, 7, 7), LocalDate.of(2026, 7, 23), LocalDate.of(2026, 8, 7),
            LocalDate.of(2026, 8, 23), LocalDate.of(2026, 9, 7), LocalDate.of(2026, 9, 23),
            LocalDate.of(2026, 10, 8), LocalDate.of(2026, 10, 23), LocalDate.of(2026, 11, 7),
            LocalDate.of(2026, 11, 22), LocalDate.of(2026, 12, 7), LocalDate.of(2026, 12, 22),
            LocalDate.of(2027, 1, 5), LocalDate.of(2027, 1, 20), LocalDate.of(2027, 2, 4),
            LocalDate.of(2027, 2, 19), LocalDate.of(2027, 3, 6), LocalDate.of(2027, 3, 21),
            LocalDate.of(2027, 4, 5), LocalDate.of(2027, 4, 20), LocalDate.of(2027, 5, 6),
            LocalDate.of(2027, 5, 21), LocalDate.of(2027, 6, 6), LocalDate.of(2027, 6, 21),
            LocalDate.of(2027, 7, 7), LocalDate.of(2027, 7, 23), LocalDate.of(2027, 8, 8),
            LocalDate.of(2027, 8, 23), LocalDate.of(2027, 9, 8), LocalDate.of(2027, 9, 23),
            LocalDate.of(2027, 10, 8), LocalDate.of(2027, 10, 23), LocalDate.of(2027, 11, 7),
            LocalDate.of(2027, 11, 22), LocalDate.of(2027, 12, 7), LocalDate.of(2027, 12, 22),
            LocalDate.of(2028, 1, 6), LocalDate.of(2028, 1, 20), LocalDate.of(2028, 2, 4),
            LocalDate.of(2028, 2, 19), LocalDate.of(2028, 3, 5), LocalDate.of(2028, 3, 20),
            LocalDate.of(2028, 4, 4), LocalDate.of(2028, 4, 19), LocalDate.of(2028, 5, 5),
            LocalDate.of(2028, 5, 20), LocalDate.of(2028, 6, 5), LocalDate.of(2028, 6, 21),
            LocalDate.of(2028, 7, 6), LocalDate.of(2028, 7, 22), LocalDate.of(2028, 8, 7),
            LocalDate.of(2028, 8, 22), LocalDate.of(2028, 9, 7), LocalDate.of(2028, 9, 22),
            LocalDate.of(2028, 10, 8), LocalDate.of(2028, 10, 23), LocalDate.of(2028, 11, 7),
            LocalDate.of(2028, 11, 22), LocalDate.of(2028, 12, 6), LocalDate.of(2028, 12, 21),
            LocalDate.of(2029, 1, 5), LocalDate.of(2029, 1, 20), LocalDate.of(2029, 2, 3),
            LocalDate.of(2029, 2, 18), LocalDate.of(2029, 3, 5), LocalDate.of(2029, 3, 20),
            LocalDate.of(2029, 4, 4), LocalDate.of(2029, 4, 20), LocalDate.of(2029, 5, 5),
            LocalDate.of(2029, 5, 21), LocalDate.of(2029, 6, 5), LocalDate.of(2029, 6, 21),
            LocalDate.of(2029, 7, 7), LocalDate.of(2029, 7, 22), LocalDate.of(2029, 8, 7),
            LocalDate.of(2029, 8, 23), LocalDate.of(2029, 9, 7), LocalDate.of(2029, 9, 23),
            LocalDate.of(2029, 10, 8), LocalDate.of(2029, 10, 23), LocalDate.of(2029, 11, 7),
            LocalDate.of(2029, 11, 22), LocalDate.of(2029, 12, 7), LocalDate.of(2029, 12, 21),
            LocalDate.of(2030, 1, 5), LocalDate.of(2030, 1, 20), LocalDate.of(2030, 2, 4),
            LocalDate.of(2030, 2, 18), LocalDate.of(2030, 3, 5), LocalDate.of(2030, 3, 20),
            LocalDate.of(2030, 4, 5), LocalDate.of(2030, 4, 20), LocalDate.of(2030, 5, 5),
            LocalDate.of(2030, 5, 21), LocalDate.of(2030, 6, 5), LocalDate.of(2030, 6, 21),
            LocalDate.of(2030, 7, 7), LocalDate.of(2030, 7, 23), LocalDate.of(2030, 8, 7),
            LocalDate.of(2030, 8, 23), LocalDate.of(2030, 9, 7), LocalDate.of(2030, 9, 23),
            LocalDate.of(2030, 10, 8), LocalDate.of(2030, 10, 23), LocalDate.of(2030, 11, 7),
            LocalDate.of(2030, 11, 22), LocalDate.of(2030, 12, 7), LocalDate.of(2030, 12, 22));

    /** 进度提示文案(≤24 字,过合规词表),按卦位固定 */
    private static final Map<Gua, String> HINTS = Map.of(
            Gua.QIAN, "全家连续 7 天都接福,就点亮",
            Gua.DUI, "累计分享 30 次福签",
            Gua.LI, "全家累计 30 天打卡早睡",
            Gua.ZHEN, "家里有 2 位以上家人一起玩",
            Gua.XUN, "累计完成 10 次「打电话」宜",
            Gua.KAN, "全家累计 30 天打卡喝水",
            Gua.GEN, "家里有人连续 30 天接福",
            Gua.KUN, "节气当天完成一件今日宜");

    private final FamilyMemberRepository memberRepository;
    private final FortuneDrawRecordRepository drawRepository;
    private final BlessEventRepository blessEventRepository;
    private final FamilyMilestoneRepository milestoneRepository;

    public BaguaService(FamilyMemberRepository memberRepository,
                        FortuneDrawRecordRepository drawRepository,
                        BlessEventRepository blessEventRepository,
                        FamilyMilestoneRepository milestoneRepository) {
        this.memberRepository = memberRepository;
        this.drawRepository = drawRepository;
        this.blessEventRepository = blessEventRepository;
        this.milestoneRepository = milestoneRepository;
    }

    /** 8 卦进度(固定顺序乾兑离震巽坎艮坤)+ 圆满/庆祝标记,单次聚合推导,只读不写 */
    @Transactional
    public BaguaStatusResponse getStatus() {
        Long userId = CurrentUser.get();
        FamilyMemberEntity member = requireMember(userId);
        return computeStatus(member.getFamilyId());
    }

    /** 圆满庆祝一次性标记(幂等):8 卦全亮才可标记,重复调用返回已标记 */
    @Transactional
    public BaguaCelebrateResponse celebrate() {
        Long userId = CurrentUser.get();
        FamilyMemberEntity member = requireMember(userId);
        Long familyId = member.getFamilyId();
        if (!computeStatus(familyId).isComplete()) {
            throw ApiException.conflict("集福阵还没点亮圆满,再陪家人攒一攒");
        }
        if (!milestoneRepository.existsByFamilyIdAndMilestone(familyId, FamilyMilestoneEntity.BAGUA_COMPLETE)) {
            FamilyMilestoneEntity milestone = new FamilyMilestoneEntity();
            milestone.setFamilyId(familyId);
            milestone.setMilestone(FamilyMilestoneEntity.BAGUA_COMPLETE);
            milestoneRepository.save(milestone);
        }
        BaguaCelebrateResponse resp = new BaguaCelebrateResponse();
        resp.setCelebrated(true);
        return resp;
    }

    private BaguaStatusResponse computeStatus(Long familyId) {
        List<FamilyMemberEntity> members = memberRepository.findByFamilyIdOrderByIdAsc(familyId);
        List<FortuneDrawRecordEntity> draws = drawRepository.findByFamilyId(familyId);

        // 一次遍历备好各口径的中间量
        Map<LocalDate, Set<Long>> drawnUsersByDate = new HashMap<>();
        Set<String> earlySleepUserDays = new HashSet<>();   // 离:早睡,去重(人,日)
        Set<String> waterUserDays = new HashSet<>();        // 坎:喝水,去重(人,日)
        int callCount = 0;                                  // 巽:打电话,计次
        int solarTermTaskCount = 0;                         // 坤:节气日完成今日宜
        for (FortuneDrawRecordEntity draw : draws) {
            drawnUsersByDate.computeIfAbsent(draw.getDate(), d -> new HashSet<>()).add(draw.getUserId());
            String taskItem = draw.getTaskItem();
            if (taskItem == null) {
                continue;
            }
            String userDay = draw.getUserId() + "|" + draw.getDate();
            if (taskItem.contains("早睡")) {
                earlySleepUserDays.add(userDay);
            }
            if (taskItem.contains("喝水")) {
                waterUserDays.add(userDay);
            }
            if (taskItem.contains("打电话")) {
                callCount++;
            }
            if (SOLAR_TERMS.contains(draw.getDate())) {
                solarTermTaskCount++;
            }
        }

        // 乾:从今日回溯,每日全员已抽,中断即止
        int memberCount = members.size();
        int familyStreak = 0;
        LocalDate day = LocalDate.now();
        while (drawnUsersByDate.getOrDefault(day, Set.of()).size() >= memberCount) {
            familyStreak++;
            day = day.minusDays(1);
        }
        // 艮:成员个人连续抽卡天数的最大值
        int maxPersonalStreak = members.stream().mapToInt(FamilyMemberEntity::getStreakDays).max().orElse(0);

        List<BaguaTrigramProgress> trigrams = new ArrayList<>();
        trigrams.add(progress(Gua.QIAN, familyStreak, 7));
        trigrams.add(progress(Gua.DUI, (int) blessEventRepository
                .countByFamilyIdAndType(familyId, BlessEventEntity.Type.SHARE_CARD), 30));
        trigrams.add(progress(Gua.LI, earlySleepUserDays.size(), 30));
        trigrams.add(progress(Gua.ZHEN, memberCount >= 2 ? memberCount - 1 : 0, 1));
        trigrams.add(progress(Gua.XUN, callCount, 10));
        trigrams.add(progress(Gua.KAN, waterUserDays.size(), 30));
        trigrams.add(progress(Gua.GEN, maxPersonalStreak, 30));
        trigrams.add(progress(Gua.KUN, solarTermTaskCount, 1));

        BaguaStatusResponse resp = new BaguaStatusResponse();
        resp.setTrigrams(trigrams);
        resp.setLitCount((int) trigrams.stream().filter(BaguaTrigramProgress::isLit).count());
        resp.setComplete(trigrams.stream().allMatch(BaguaTrigramProgress::isLit));
        resp.setCelebrated(resp.isComplete() && milestoneRepository
                .existsByFamilyIdAndMilestone(familyId, FamilyMilestoneEntity.BAGUA_COMPLETE));
        return resp;
    }

    private BaguaTrigramProgress progress(Gua gua, int current, int target) {
        BaguaTrigramProgress p = new BaguaTrigramProgress();
        p.setGua(gua);
        p.setCurrent(Math.max(0, current));
        p.setTarget(target);
        p.setLit(p.getCurrent() >= target);
        p.setHint(HINTS.get(gua));
        return p;
    }

    private FamilyMemberEntity requireMember(Long userId) {
        return memberRepository.findByUserId(userId)
                .orElseThrow(() -> ApiException.conflict("您还没有加入「家」,请您先创建或从家人邀请卡片进入"));
    }
}
