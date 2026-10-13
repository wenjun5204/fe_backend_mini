package com.example.backend.service;

import com.example.backend.common.ApiException;
import com.example.backend.common.CurrentUser;
import com.example.backend.dto.NotifyDtos.NotifyStatusResponse;
import com.example.backend.dto.NotifyDtos.NotifySubscribeRequest;
import com.example.backend.dto.NotifyDtos.NotifySubscribeResponse;
import com.example.backend.entity.NotifyGrantEntity;
import com.example.backend.repository.NotifyGrantRepository;
import com.example.backend.repository.NotifySendRepository;
import java.time.LocalDate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 订阅消息额度账本:v1.5 只登记授权与计算剩余额度,不做真实微信 API 发送(演进项)。
 * 口径:remainingQuotaToday = 累计接受授权数 - 当日已发送数,下限 0;每日上限 4 条。
 */
@Service
public class NotifyService {

    /** 每日发送上限(条/人/天),设置页展示「您每天最多收到 4 条提醒,不会多打扰」 */
    public static final int DAILY_LIMIT = 4;

    private final NotifyGrantRepository grantRepository;
    private final NotifySendRepository sendRepository;

    public NotifyService(NotifyGrantRepository grantRepository, NotifySendRepository sendRepository) {
        this.grantRepository = grantRepository;
        this.sendRepository = sendRepository;
    }

    /** 登记授权(拒绝也登记,用于频率透明展示);返回今日剩余可发送条数 */
    @Transactional
    public NotifySubscribeResponse subscribe(NotifySubscribeRequest request) {
        Long userId = CurrentUser.get();
        String templateId = request.getTemplateId();
        if (templateId == null || templateId.isBlank()) {
            throw ApiException.param("请选择要允许的提醒");
        }
        if (templateId.length() > 64) {
            throw ApiException.param("提醒模板编号不对,请重试");
        }

        NotifyGrantEntity grant = new NotifyGrantEntity();
        grant.setUserId(userId);
        grant.setTemplateId(templateId.trim());
        grant.setAccepted(request.isAccepted());
        grantRepository.save(grant);

        NotifySubscribeResponse resp = new NotifySubscribeResponse();
        resp.setRegistered(true);
        resp.setRemainingQuotaToday(remainingQuotaToday(userId));
        return resp;
    }

    /** 查询授权状态与剩余额度(subscribed = 存在有效授权额度) */
    @Transactional
    public NotifyStatusResponse getStatus() {
        Long userId = CurrentUser.get();
        int remaining = remainingQuotaToday(userId);
        NotifyStatusResponse resp = new NotifyStatusResponse();
        resp.setSubscribed(remaining > 0);
        resp.setRemainingQuotaToday(remaining);
        resp.setDailyLimit(DAILY_LIMIT);
        return resp;
    }

    /** 剩余额度:累计接受授权数 - 当日已发送数,下限 0 */
    private int remainingQuotaToday(Long userId) {
        long acceptedGrants = grantRepository.countByUserIdAndAcceptedTrue(userId);
        long sendsToday = sendRepository.countByUserIdAndSentAtGreaterThanEqual(
                userId, LocalDate.now().atStartOfDay());
        return (int) Math.max(0, acceptedGrants - sendsToday);
    }
}
