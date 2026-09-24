package com.example.backend.repository;

import com.example.backend.entity.FamilyMemberEntity;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface FamilyMemberRepository extends JpaRepository<FamilyMemberEntity, Long> {
    Optional<FamilyMemberEntity> findByUserId(Long userId);
    List<FamilyMemberEntity> findByFamilyIdOrderByIdAsc(Long familyId);
    long countByFamilyId(Long familyId);

    /** 原子累加个人福值(数据库层自增,避免读-改-写丢更新);返回更新后的值 */
    @Modifying
    @Query("UPDATE FamilyMemberEntity m SET m.personalBless = m.personalBless + :amount WHERE m.userId = :userId")
    long incrementPersonalBless(@Param("userId") Long userId, @Param("amount") int amount);
}
