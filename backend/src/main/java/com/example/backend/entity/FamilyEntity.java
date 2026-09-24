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

@Entity
@Table(name = "family")
public class FamilyEntity {

    public static final int MEMBER_LIMIT = 8;

    /** 门牌等级阈值:勤俭之家500 / 和睦之家2000 / 满门福气5000 / 福泽满堂20000,只升不降 */
    public static long plateThreshold(FortuneDtos.PlateLevel level) {
        return switch (level) {
            case NONE -> 0;
            case BRONZE -> 500;
            case SILVER -> 2000;
            case GOLD -> 5000;
            case JADE -> 20000;
        };
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 10)
    private String name;

    @Column(nullable = false, unique = true, length = 8)
    private String inviteCode;

    @Column(nullable = false)
    private Long ownerUserId;

    @Column(nullable = false)
    private long totalBless = 0;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 8)
    private FortuneDtos.PlateLevel plateLevel = FortuneDtos.PlateLevel.NONE;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getInviteCode() { return inviteCode; }
    public void setInviteCode(String inviteCode) { this.inviteCode = inviteCode; }
    public Long getOwnerUserId() { return ownerUserId; }
    public void setOwnerUserId(Long ownerUserId) { this.ownerUserId = ownerUserId; }
    public long getTotalBless() { return totalBless; }
    public void setTotalBless(long totalBless) { this.totalBless = totalBless; }
    public FortuneDtos.PlateLevel getPlateLevel() { return plateLevel; }
    public void setPlateLevel(FortuneDtos.PlateLevel plateLevel) { this.plateLevel = plateLevel; }
}
