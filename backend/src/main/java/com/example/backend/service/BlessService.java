package com.example.backend.service;

import com.example.backend.dto.FortuneDtos.PlateLevel;
import com.example.backend.entity.BlessEventEntity;
import com.example.backend.entity.FamilyEntity;
import com.example.backend.entity.FamilyMemberEntity;
import com.example.backend.repository.BlessEventRepository;
import com.example.backend.repository.FamilyMemberRepository;
import com.example.backend.repository.FamilyRepository;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 福值结算:所有福值变更的唯一路径。
 * 规则:只增不减,写 BlessEvent(可审计)→ 同步个人/家族累计 → 检查门牌升级。
 */
@Service
public class BlessService {

    private final BlessEventRepository blessEventRepository;
    private final FamilyRepository familyRepository;
    private final FamilyMemberRepository familyMemberRepository;

    public BlessService(BlessEventRepository blessEventRepository,
                        FamilyRepository familyRepository,
                        FamilyMemberRepository familyMemberRepository) {
        this.blessEventRepository = blessEventRepository;
        this.familyRepository = familyRepository;
        this.familyMemberRepository = familyMemberRepository;
    }

    /**
     * 发放福值(事务内);返回发放后的结果快照。
     * 并发安全:累计字段用数据库原子自增(UPDATE x = x + ?),不经过实体读-改-写,
     * 多人同时添福不会丢更新;门牌只升不降用条件更新保护。
     */
    @Transactional
    public GrantResult grant(Long userId, Long familyId, BlessEventEntity.Type type, Long targetUserId) {
        // 1. 写事件(唯一福值变更路径,可审计)
        BlessEventEntity event = new BlessEventEntity();
        event.setUserId(userId);
        event.setFamilyId(familyId);
        event.setType(type);
        event.setAmount(type.amount());
        event.setTargetUserId(targetUserId);
        blessEventRepository.save(event);

        // 2. 原子累加个人福值(被添福时累计到目标用户),重读快照
        Long blessOwner = targetUserId != null ? targetUserId : userId;
        familyMemberRepository.findByUserId(blessOwner)
                .orElseThrow(() -> new IllegalStateException("成员不存在: " + blessOwner));
        familyMemberRepository.incrementPersonalBless(blessOwner, type.amount());
        FamilyMemberEntity member = familyMemberRepository.findByUserId(blessOwner)
                .orElseThrow(() -> new IllegalStateException("成员不存在: " + blessOwner));

        // 3. 原子累加家族福池,重读后条件升级门牌(只升不降)
        familyRepository.findById(familyId)
                .orElseThrow(() -> new IllegalStateException("家族不存在: " + familyId));
        familyRepository.incrementTotalBless(familyId, type.amount());
        FamilyEntity family = familyRepository.findById(familyId)
                .orElseThrow(() -> new IllegalStateException("家族不存在: " + familyId));
        PlateLevel newLevel = upgradePlate(family.getTotalBless());
        if (newLevel != family.getPlateLevel() && newLevel.ordinal() > family.getPlateLevel().ordinal()) {
            family.setPlateLevel(newLevel);
            familyRepository.save(family);
        }

        return new GrantResult(type.amount(), member.getPersonalBless(),
                family.getTotalBless(), family.getPlateLevel());
    }

    /** 门牌等级:按累计福值取可达最高级,只升不降 */
    public static PlateLevel upgradePlate(long totalBless) {
        PlateLevel level = PlateLevel.NONE;
        for (PlateLevel pl : List.of(PlateLevel.BRONZE, PlateLevel.SILVER, PlateLevel.GOLD, PlateLevel.JADE)) {
            if (totalBless >= FamilyEntity.plateThreshold(pl)) {
                level = pl;
            }
        }
        return level;
    }

    /** 距下一级门牌还差的福值 */
    public static long nextPlateGap(long totalBless) {
        for (PlateLevel pl : List.of(PlateLevel.BRONZE, PlateLevel.SILVER, PlateLevel.GOLD, PlateLevel.JADE)) {
            long threshold = FamilyEntity.plateThreshold(pl);
            if (totalBless < threshold) {
                return threshold - totalBless;
            }
        }
        return 0;
    }

    public record GrantResult(int amount, long personalBless, long familyTotalBless, PlateLevel plateLevel) {
    }
}
