package com.example.backend.repository;

import com.example.backend.dto.FortuneDtos;
import com.example.backend.entity.FamilyEntity;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface FamilyRepository extends JpaRepository<FamilyEntity, Long> {
    Optional<FamilyEntity> findByInviteCode(String inviteCode);
    List<FamilyEntity> findByPlateLevel(FortuneDtos.PlateLevel level);
    boolean existsByInviteCode(String inviteCode);

    /**
     * 原子累加家族福池(数据库层自增,避免读-改-写丢更新):
     * 家族福池是全家族共写热点行,读-改-写并发下会互相覆盖丢福值。
     * 返回更新后的 totalBless,供门牌升级判定与响应快照使用。
     */
    @Modifying
    @Query("UPDATE FamilyEntity f SET f.totalBless = f.totalBless + :amount WHERE f.id = :id")
    long incrementTotalBless(@Param("id") Long id, @Param("amount") int amount);
}
