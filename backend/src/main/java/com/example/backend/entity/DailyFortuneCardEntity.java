package com.example.backend.entity;

import com.example.backend.dto.FortuneDtos;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.LocalDate;

/** 每日福签:一个家族一天一条(全家同签),生成后当日固定 */
@Entity
@Table(name = "daily_fortune_card", uniqueConstraints = {
        @UniqueConstraint(name = "uk_card_family_date", columnNames = {"familyId", "date"})
})
public class DailyFortuneCardEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long familyId;

    @Column(nullable = false)
    private LocalDate date;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 8)
    private FortuneDtos.FortuneLevel level = FortuneDtos.FortuneLevel.PINGAN;

    /** 今日宜条目,逗号分隔,2-3 项,生活化正面建议,永不出现忌 */
    @Column(nullable = false, length = 200)
    private String yiItems;

    @Column(nullable = false, length = 50)
    private String blessText;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getFamilyId() { return familyId; }
    public void setFamilyId(Long familyId) { this.familyId = familyId; }
    public LocalDate getDate() { return date; }
    public void setDate(LocalDate date) { this.date = date; }
    public FortuneDtos.FortuneLevel getLevel() { return level; }
    public void setLevel(FortuneDtos.FortuneLevel level) { this.level = level; }
    public String getYiItems() { return yiItems; }
    public void setYiItems(String yiItems) { this.yiItems = yiItems; }
    public String getBlessText() { return blessText; }
    public void setBlessText(String blessText) { this.blessText = blessText; }
}
