package com.example.backend.service;

import com.example.backend.common.ApiException;
import com.example.backend.common.CurrentUser;
import com.example.backend.dto.JieyouDtos.JieyouQuoteResponse;
import com.example.backend.entity.JieyouDailyRecordEntity;
import com.example.backend.repository.JieyouDailyRecordRepository;
import jakarta.annotation.PostConstruct;
import java.time.LocalDate;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 解忧册:每人每日一翻,服务端幂等(同日重复请求返回同条);纯随机,不接收不存储任何问题内容。
 */
@Service
public class JieyouService {

    private final JieyouDailyRecordRepository recordRepository;

    public JieyouService(JieyouDailyRecordRepository recordRepository) {
        this.recordRepository = recordRepository;
    }

    /** 启动期合规断言:全量宽心话扫描违禁词表,命中即抛异常拒绝启动 */
    @PostConstruct
    public void validateQuoteLib() {
        JieyouQuoteLib.validate();
    }

    @Transactional
    public JieyouQuoteResponse getQuote() {
        Long userId = CurrentUser.get();
        LocalDate today = LocalDate.now();
        return recordRepository.findByUserIdAndDate(userId, today)
                .<JieyouQuoteResponse>map(record -> toResponse(JieyouQuoteLib.byId(record.getQuoteId())))
                .orElseGet(() -> drawAndRecord(userId, today));
    }

    /** 当日首翻:纯随机选一条落库(同日同页,幂等);并发同日首翻由唯一约束兜底回读 */
    private JieyouQuoteResponse drawAndRecord(Long userId, LocalDate today) {
        JieyouQuoteLib.Quote quote = JieyouQuoteLib.randomQuote();
        JieyouDailyRecordEntity record = new JieyouDailyRecordEntity();
        record.setUserId(userId);
        record.setDate(today);
        record.setQuoteId(quote.id());
        try {
            recordRepository.saveAndFlush(record);
        } catch (DataIntegrityViolationException e) {
            // 并发兜底:同日重复落库唯一约束冲突,回读当日已固定那条
            JieyouDailyRecordEntity existing = recordRepository.findByUserIdAndDate(userId, today)
                    .orElseThrow(() -> ApiException.conflict("今天这一页先缓缓,请您稍后再翻"));
            return toResponse(JieyouQuoteLib.byId(existing.getQuoteId()));
        }
        return toResponse(quote);
    }

    /** 页码 pageNo = quoteId(1-120,前端展示用) */
    private JieyouQuoteResponse toResponse(JieyouQuoteLib.Quote quote) {
        JieyouQuoteResponse resp = new JieyouQuoteResponse();
        resp.setQuoteId(quote.id());
        resp.setType(quote.type());
        resp.setText(quote.text());
        resp.setPageNo(quote.id());
        return resp;
    }
}
