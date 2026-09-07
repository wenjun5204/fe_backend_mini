package com.example.backend.service;

import com.example.backend.common.ApiException;
import com.example.backend.common.CurrentUser;
import com.example.backend.dto.FamilyDtos.TargetUserRequest;
import com.example.backend.dto.FortuneDtos.BlessResult;
import com.example.backend.dto.FortuneDtos.CuifuResult;
import com.example.backend.entity.BlessEventEntity;
import com.example.backend.entity.FamilyMemberEntity;
import com.example.backend.entity.InteractRecordEntity;
import com.example.backend.repository.FamilyMemberRepository;
import com.example.backend.repository.InteractRecordRepository;
import java.time.LocalDate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 互助互动:添福(1 次/人/对人/天,+8)与催福(3 次/人/对人/天,提醒未接福家人)。
 * 文案原则:全家在等您(归属感),绝不批评。
 */
@Service
public class InteractService {

    private final InteractRecordRepository interactRepository;
    private final FamilyMemberRepository memberRepository;
    private final BlessService blessService;

    public InteractService(InteractRecordRepository interactRepository,
                           FamilyMemberRepository memberRepository,
                           BlessService blessService) {
        this.interactRepository = interactRepository;
        this.memberRepository = memberRepository;
        this.blessService = blessService;
    }

    @Transactional
    public BlessResult tianfu(TargetUserRequest request) {
        Long userId = CurrentUser.get();
        FamilyMemberEntity me = requireMember(userId);
        Long targetUserId = requireTarget(request, me);

        long used = interactRepository.countByUserIdAndTargetUserIdAndDateAndType(
                userId, targetUserId, LocalDate.now(), InteractRecordEntity.Type.TIANFU);
        if (used >= InteractRecordEntity.TIANFU_DAILY_LIMIT) {
            throw ApiException.rateLimited("您今天已经给这位家人添过福啦,明天再来");
        }
        record(userId, targetUserId, InteractRecordEntity.Type.TIANFU);

        // 通知预留位:正式环境此处发送订阅消息「{name}给您添福啦,全家福值+8」
        BlessService.GrantResult result = blessService.grant(userId, me.getFamilyId(),
                BlessEventEntity.Type.TIAN_FU, targetUserId);

        BlessResult resp = new BlessResult();
        resp.setAmount(result.amount());
        resp.setPersonalBless(result.personalBless());
        resp.setFamilyTotalBless(result.familyTotalBless());
        resp.setPlateLevel(result.plateLevel());
        return resp;
    }

    @Transactional
    public CuifuResult cuifu(TargetUserRequest request) {
        Long userId = CurrentUser.get();
        FamilyMemberEntity me = requireMember(userId);
        Long targetUserId = requireTarget(request, me);

        FamilyMemberEntity target = memberRepository.findByUserId(targetUserId)
                .orElseThrow(() -> ApiException.notFound("没有找到这位家人"));
        // 只能催今日未接福的家人
        if (LocalDate.now().equals(target.getLastDrawDate())) {
            throw ApiException.conflict("这位家人今天已经接过福啦");
        }

        long used = interactRepository.countByUserIdAndTargetUserIdAndDateAndType(
                userId, targetUserId, LocalDate.now(), InteractRecordEntity.Type.CUIFU);
        if (used >= InteractRecordEntity.CUIFU_DAILY_LIMIT) {
            throw ApiException.rateLimited("今天催的次数有点多啦,给家人留点空间");
        }
        record(userId, targetUserId, InteractRecordEntity.Type.CUIFU);

        // 通知预留位:正式环境此处发送订阅消息「{name}请您接今日福卡,全家人就等您啦」
        CuifuResult resp = new CuifuResult();
        resp.setSent(true);
        resp.setRemainCount((int) (InteractRecordEntity.CUIFU_DAILY_LIMIT - used - 1));
        return resp;
    }

    private void record(Long userId, Long targetUserId, InteractRecordEntity.Type type) {
        InteractRecordEntity r = new InteractRecordEntity();
        r.setUserId(userId);
        r.setTargetUserId(targetUserId);
        r.setDate(LocalDate.now());
        r.setType(type);
        interactRepository.save(r);
    }

    private Long requireTarget(TargetUserRequest request, FamilyMemberEntity me) {
        if (request == null || request.getTargetUserId() == null) {
            throw ApiException.param("请您选择要互动的家人");
        }
        if (request.getTargetUserId().equals(me.getUserId())) {
            throw ApiException.param("不能对自己操作哦");
        }
        FamilyMemberEntity target = memberRepository.findByUserId(request.getTargetUserId())
                .filter(t -> t.getFamilyId().equals(me.getFamilyId()))
                .orElseThrow(() -> ApiException.notFound("这位家人不在您的「家」里"));
        return target.getUserId();
    }

    private FamilyMemberEntity requireMember(Long userId) {
        return memberRepository.findByUserId(userId)
                .orElseThrow(() -> ApiException.conflict("您还没有加入「家」,请您先创建或从家人邀请卡片进入"));
    }
}
