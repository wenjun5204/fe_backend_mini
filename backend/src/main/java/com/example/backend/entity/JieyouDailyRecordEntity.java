package com.example.backend.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.LocalDate;

/** 解忧册当日翻页记录:user+date 唯一,当日首翻固定(幂等),只存页码不存问题内容 */
@Entity
@Table(name = "jieyou_daily_record", uniqueConstraints = {
        @UniqueConstraint(name = "uk_jieyou_user_date", columnNames = {"userId", "date"})
})
public class JieyouDailyRecordEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long userId;

    @Column(nullable = false)
    private LocalDate date;

    /** 册页码(= quoteId,1-120) */
    @Column(nullable = false)
    private int quoteId;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }
    public LocalDate getDate() { return date; }
    public void setDate(LocalDate date) { this.date = date; }
    public int getQuoteId() { return quoteId; }
    public void setQuoteId(int quoteId) { this.quoteId = quoteId; }
}
