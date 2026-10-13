package com.example.backend;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.backend.common.ApiException;
import com.example.backend.common.CurrentUser;
import com.example.backend.dto.NotifyDtos.NotifyStatusResponse;
import com.example.backend.dto.NotifyDtos.NotifySubscribeRequest;
import com.example.backend.dto.NotifyDtos.NotifySubscribeResponse;
import com.example.backend.entity.NotifyGrantEntity;
import com.example.backend.repository.NotifyGrantRepository;
import com.example.backend.repository.NotifySendRepository;
import com.example.backend.service.NotifyService;
import java.time.LocalDate;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/** 频控规则测试:订阅授权登记(拒绝也登记),剩余额度 = 接受授权数 - 当日已发送,下限 0 */
@ExtendWith(MockitoExtension.class)
class NotifyServiceTest {

    @Mock
    private NotifyGrantRepository grantRepository;
    @Mock
    private NotifySendRepository sendRepository;

    private NotifyService service;

    private static final Long ME = 1L;

    @BeforeEach
    void setup() {
        service = new NotifyService(grantRepository, sendRepository);
        CurrentUser.set(ME);
    }

    @AfterEach
    void cleanup() {
        CurrentUser.clear();
    }

    private NotifySubscribeRequest request(String templateId, boolean accepted) {
        NotifySubscribeRequest req = new NotifySubscribeRequest();
        req.setTemplateId(templateId);
        req.setAccepted(accepted);
        return req;
    }

    @Test
    void 模板编号为空返回40000() {
        ApiException e = assertThrows(ApiException.class,
                () -> service.subscribe(request("  ", true)));
        assertEquals("40000", e.getCode());
    }

    @Test
    void 拒绝授权也登记() {
        when(grantRepository.countByUserIdAndAcceptedTrue(ME)).thenReturn(0L);
        when(sendRepository.countByUserIdAndSentAtGreaterThanEqual(eq(ME), any()))
                .thenReturn(0L);

        NotifySubscribeResponse resp = service.subscribe(request("TPL-1", false));
        assertTrue(resp.isRegistered());
        assertEquals(0, resp.getRemainingQuotaToday());

        ArgumentCaptor<NotifyGrantEntity> captor = ArgumentCaptor.forClass(NotifyGrantEntity.class);
        verify(grantRepository).save(captor.capture());
        assertEquals("TPL-1", captor.getValue().getTemplateId());
        assertEquals(false, captor.getValue().isAccepted());
    }

    @Test
    void 剩余额度为接受授权数减当日发送() {
        when(grantRepository.countByUserIdAndAcceptedTrue(ME)).thenReturn(2L);
        when(sendRepository.countByUserIdAndSentAtGreaterThanEqual(eq(ME), any()))
                .thenReturn(1L);

        NotifySubscribeResponse resp = service.subscribe(request("TPL-1", true));
        assertEquals(1, resp.getRemainingQuotaToday());
    }

    @Test
    void 剩余额度下限为零() {
        when(grantRepository.countByUserIdAndAcceptedTrue(ME)).thenReturn(1L);
        when(sendRepository.countByUserIdAndSentAtGreaterThanEqual(eq(ME), any()))
                .thenReturn(3L);

        NotifySubscribeResponse resp = service.subscribe(request("TPL-1", true));
        assertEquals(0, resp.getRemainingQuotaToday());
    }

    @Test
    void 状态接口展示每日上限与额度() {
        when(grantRepository.countByUserIdAndAcceptedTrue(ME)).thenReturn(1L);
        when(sendRepository.countByUserIdAndSentAtGreaterThanEqual(
                eq(ME), eq(LocalDate.now().atStartOfDay()))).thenReturn(0L);

        NotifyStatusResponse resp = service.getStatus();
        assertTrue(resp.isSubscribed());
        assertEquals(1, resp.getRemainingQuotaToday());
        assertEquals(NotifyService.DAILY_LIMIT, resp.getDailyLimit());
        assertEquals(4, resp.getDailyLimit());
    }

    @Test
    void 无有效额度时未订阅() {
        when(grantRepository.countByUserIdAndAcceptedTrue(ME)).thenReturn(0L);
        when(sendRepository.countByUserIdAndSentAtGreaterThanEqual(eq(ME), any()))
                .thenReturn(0L);

        NotifyStatusResponse resp = service.getStatus();
        assertEquals(false, resp.isSubscribed());
        assertEquals(0, resp.getRemainingQuotaToday());
    }
}
