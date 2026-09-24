package com.example.backend;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.backend.common.CurrentUser;
import com.example.backend.dto.JieyouDtos.JieyouQuoteResponse;
import com.example.backend.entity.JieyouDailyRecordEntity;
import com.example.backend.repository.JieyouDailyRecordRepository;
import com.example.backend.service.JieyouQuoteLib;
import com.example.backend.service.JieyouService;
import java.time.LocalDate;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/** 解忧册幂等测试:当日已翻返回同一条(不重复落库),当日首翻随机落库,页码与文案库一致 */
@ExtendWith(MockitoExtension.class)
class JieyouServiceTest {

    @Mock
    private JieyouDailyRecordRepository recordRepository;

    private JieyouService service;

    private static final Long ME = 1L;

    @BeforeEach
    void setup() {
        service = new JieyouService(recordRepository);
        CurrentUser.set(ME);
    }

    @AfterEach
    void cleanup() {
        CurrentUser.clear();
    }

    @Test
    void 当日已翻返回同一条且不落库() {
        JieyouDailyRecordEntity existing = new JieyouDailyRecordEntity();
        existing.setUserId(ME);
        existing.setDate(LocalDate.now());
        existing.setQuoteId(42);
        when(recordRepository.findByUserIdAndDate(ME, LocalDate.now()))
                .thenReturn(Optional.of(existing));

        JieyouQuoteResponse resp = service.getQuote();
        assertEquals(42, resp.getQuoteId());
        assertEquals(42, resp.getPageNo());
        assertEquals(JieyouQuoteLib.byId(42).text(), resp.getText());
        verify(recordRepository, never()).saveAndFlush(any());
    }

    @Test
    void 当日首翻随机落库且页码与正文一致() {
        when(recordRepository.findByUserIdAndDate(ME, LocalDate.now()))
                .thenReturn(Optional.empty());

        JieyouQuoteResponse resp = service.getQuote();
        assertTrue(resp.getQuoteId() >= 1 && resp.getQuoteId() <= JieyouQuoteLib.QUOTE_COUNT);
        assertEquals(resp.getQuoteId(), resp.getPageNo());
        assertEquals(JieyouQuoteLib.byId(resp.getQuoteId()).text(), resp.getText());
        assertEquals(JieyouQuoteLib.byId(resp.getQuoteId()).type(), resp.getType());

        ArgumentCaptor<JieyouDailyRecordEntity> captor =
                ArgumentCaptor.forClass(JieyouDailyRecordEntity.class);
        verify(recordRepository).saveAndFlush(captor.capture());
        assertEquals(ME, captor.getValue().getUserId());
        assertEquals(resp.getQuoteId(), captor.getValue().getQuoteId());
    }
}
