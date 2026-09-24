package com.example.backend.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.LocalDateTime;

/** 家族一次性里程碑:family+milestone 唯一(如八卦圆满庆祝 BAGUA_COMPLETE,防重复) */
@Entity
@Table(name = "family_milestone", uniqueConstraints = {
        @UniqueConstraint(name = "uk_milestone_family_type", columnNames = {"familyId", "milestone"})
})
public class FamilyMilestoneEntity {

    /** 八卦圆满庆祝(8 卦全亮后的动效触发标记) */
    public static final String BAGUA_COMPLETE = "BAGUA_COMPLETE";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long familyId;

    @Column(nullable = false, length = 32)
    private String milestone;

    @Column(nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getFamilyId() { return familyId; }
    public void setFamilyId(Long familyId) { this.familyId = familyId; }
    public String getMilestone() { return milestone; }
    public void setMilestone(String milestone) { this.milestone = milestone; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
