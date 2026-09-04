package com.example.backend.service;

import com.example.backend.common.ApiException;
import com.example.backend.common.CurrentUser;
import com.example.backend.dto.FortuneDtos.BlessResult;
import com.example.backend.dto.FortuneDtos.FortuneCard;
import com.example.backend.dto.FortuneDtos.FortuneDrawResponse;
import com.example.backend.dto.FortuneDtos.FortuneTodayResponse;
import com.example.backend.entity.BlessEventEntity;
import com.example.backend.entity.DailyFortuneCardEntity;
import com.example.backend.entity.FamilyEntity;
import com.example.backend.entity.FamilyMemberEntity;
import com.example.backend.entity.FortuneDrawRecordEntity;
import com.example.backend.repository.DailyFortuneCardRepository;
import com.example.backend.repository.FamilyMemberRepository;
import com.example.backend.repository.FamilyRepository;
import com.example.backend.repository.FortuneDrawRecordRepository;
import java.time.LocalDate;
import java.util.Arrays;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 每日福签:全家同签,等级 70/25/5,「今日宜」只宜不忌。
 * 定时任务每日 0 点生成;drawFortune 兜底补生成,避免定时失败阻塞。
 */
@Service
public class FortuneService {

    private final DailyFortuneCardRepository cardRepository;
    private final FortuneDrawRecordRepository drawRepository;
    private final FamilyMemberRepository memberRepository;
    private final FamilyRepository familyRepository;
    private final BlessService blessService;

    public FortuneService(DailyFortuneCardRepository cardRepository,
                          FortuneDrawRecordRepository drawRepository,
                          FamilyMemberRepository memberRepository,
                          FamilyRepository familyRepository,
                          BlessService blessService) {
        this.cardRepository = cardRepository;
        this.drawRepository = drawRepository;
        this.memberRepository = memberRepository;
        this.familyRepository = familyRepository;
        this.blessService = blessService;
    }

    /** 每日 0 点为所有家族生成当日福签(全家同签) */
    @Scheduled(cron = "0 0 0 * * *")
    public void generateDailyCards() {
        LocalDate today = LocalDate.now();
        for (FamilyEntity family : familyRepository.findAll()) {
            ensureCard(family.getId(), today);
        }
    }

    @Transactional(readOnly = true)
    public FortuneTodayResponse getTodayFortune() {
        Long userId = CurrentUser.get();
        FamilyMemberEntity member = requireMember(userId);
        DailyFortuneCardEntity card = ensureCard(member.getFamilyId(), LocalDate.now());
        FortuneDrawRecordEntity record = drawRepository.findByUserIdAndDate(userId, card.getDate())
                .orElse(null);

        FortuneTodayResponse resp = new FortuneTodayResponse();
        resp.setCard(toCardDto(card));
        resp.setDrawn(record != null);
        resp.setShared(record != null && record.isShared());
        resp.setTaskDone(record != null && record.isTaskDone());
        resp.setPersonalBless(member.getPersonalBless());
        return resp;
    }

    @Transactional
    public FortuneDrawResponse drawFortune() {
        Long userId = CurrentUser.get();
        FamilyMemberEntity member = requireMember(userId);
        LocalDate today = LocalDate.now();
        DailyFortuneCardEntity card = ensureCard(member.getFamilyId(), today);

        if (drawRepository.findByUserIdAndDate(userId, today).isPresent()) {
            throw ApiException.conflict("您今天已经接过福啦,明早 7:30 再来");
        }

        // 连续天数:昨天接过 → +1,否则重置为 1(福值不清零,只熄火焰)
        int streak = today.minusDays(1).equals(member.getLastDrawDate())
                ? member.getStreakDays() + 1 : 1;
        member.setStreakDays(streak);
        member.setLastDrawDate(today);
        memberRepository.save(member);

        FortuneDrawRecordEntity record = new FortuneDrawRecordEntity();
        record.setUserId(userId);
        record.setFamilyId(member.getFamilyId());
        record.setDate(today);
        drawRepository.save(record);

        BlessService.GrantResult result = blessService.grant(userId, member.getFamilyId(),
                BlessEventEntity.Type.DRAW_CARD, null);

        FortuneDrawResponse resp = new FortuneDrawResponse();
        resp.setCard(toCardDto(card));
        resp.setPersonalBless(result.personalBless());
        resp.setFamilyTotalBless(result.familyTotalBless());
        resp.setPraiseText(FortuneTextLib.praiseText(streak));
        return resp;
    }

    @Transactional
    public BlessResult shareFortune() {
        return oneShotDailyAction(true);
    }

    @Transactional
    public BlessResult completeFortuneTask() {
        return oneShotDailyAction(false);
    }

    /** 分享/完成任务共用:每日 1 次,幂等防重 */
    private BlessResult oneShotDailyAction(boolean share) {
        Long userId = CurrentUser.get();
        FamilyMemberEntity member = requireMember(userId);
        LocalDate today = LocalDate.now();
        FortuneDrawRecordEntity record = drawRepository.findByUserIdAndDate(userId, today)
                .orElseThrow(() -> ApiException.conflict("请您先接今日福签"));

        if (share && record.isShared()) {
            throw ApiException.conflict("今天的福已经带给家人啦");
        }
        if (!share && record.isTaskDone()) {
            throw ApiException.conflict("今天的事已经完成啦,您真棒");
        }

        if (share) {
            record.setShared(true);
        } else {
            record.setTaskDone(true);
        }
        drawRepository.save(record);

        BlessService.GrantResult result = blessService.grant(userId, member.getFamilyId(),
                share ? BlessEventEntity.Type.SHARE_CARD : BlessEventEntity.Type.TASK_DONE, null);

        BlessResult resp = new BlessResult();
        resp.setAmount(result.amount());
        resp.setPersonalBless(result.personalBless());
        resp.setFamilyTotalBless(result.familyTotalBless());
        resp.setPlateLevel(result.plateLevel());
        return resp;
    }

    /** 兜底生成:当日无签则即时补 */
    private DailyFortuneCardEntity ensureCard(Long familyId, LocalDate date) {
        return cardRepository.findByFamilyIdAndDate(familyId, date).orElseGet(() -> {
            DailyFortuneCardEntity card = new DailyFortuneCardEntity();
            card.setFamilyId(familyId);
            card.setDate(date);
            card.setLevel(FortuneTextLib.drawLevel());
            card.setYiItems(String.join(",", FortuneTextLib.randomYiItems()));
            card.setBlessText(FortuneTextLib.randomBlessText());
            return cardRepository.save(card);
        });
    }

    private FortuneCard toCardDto(DailyFortuneCardEntity entity) {
        FortuneCard card = new FortuneCard();
        card.setFamilyId(entity.getFamilyId());
        card.setDate(entity.getDate().toString());
        card.setLevel(entity.getLevel());
        card.setYiItems(Arrays.asList(entity.getYiItems().split(",")));
        card.setBlessText(entity.getBlessText());
        return card;
    }

    private FamilyMemberEntity requireMember(Long userId) {
        return memberRepository.findByUserId(userId)
                .orElseThrow(() -> ApiException.conflict("您还没有加入「家」,请您先创建或从家人邀请卡片进入"));
    }
}
