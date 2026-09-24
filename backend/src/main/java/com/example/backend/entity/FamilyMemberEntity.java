package com.example.backend.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDate;

@Entity
@Table(name = "family_member", uniqueConstraints = {
        @jakarta.persistence.UniqueConstraint(name = "uk_member_user", columnNames = "userId")
})
public class FamilyMemberEntity {

    public enum Role { OWNER, MEMBER }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long familyId;

    @Column(nullable = false)
    private Long userId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 8)
    private Role role = Role.MEMBER;

    @Column(length = 10)
    private String remark;

    @Column(nullable = false)
    private long personalBless = 0;

    @Column(nullable = false)
    private int streakDays = 0;

    @Column
    private LocalDate lastDrawDate;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getFamilyId() { return familyId; }
    public void setFamilyId(Long familyId) { this.familyId = familyId; }
    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }
    public Role getRole() { return role; }
    public void setRole(Role role) { this.role = role; }
    public String getRemark() { return remark; }
    public void setRemark(String remark) { this.remark = remark; }
    public long getPersonalBless() { return personalBless; }
    public void setPersonalBless(long personalBless) { this.personalBless = personalBless; }
    public int getStreakDays() { return streakDays; }
    public void setStreakDays(int streakDays) { this.streakDays = streakDays; }
    public LocalDate getLastDrawDate() { return lastDrawDate; }
    public void setLastDrawDate(LocalDate lastDrawDate) { this.lastDrawDate = lastDrawDate; }
}
