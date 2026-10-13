package com.example.backend.repository;

import com.example.backend.entity.DayRecordEntity;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface DayRecordRepository extends JpaRepository<DayRecordEntity, Long> {

    /**
     * 可见集合:本人全部 + 同家族他人 FAMILY。
     * 无家族用户 familyId 传 null,null 比较永不命中,等价只查本人。
     */
    @Query("SELECT d FROM DayRecordEntity d WHERE d.userId = :userId " +
           "OR (d.familyId = :familyId AND d.visibility = 'FAMILY')")
    List<DayRecordEntity> findVisible(@Param("userId") Long userId, @Param("familyId") Long familyId);
}
