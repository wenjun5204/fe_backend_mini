package com.example.backend.repository;

import com.example.backend.entity.BlessEventEntity;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface BlessEventRepository extends JpaRepository<BlessEventEntity, Long> {

    /** 本周(自 weekStart 起)某家族各成员的福值汇总,家内榜口径 */
    @Query("select e.userId, sum(e.amount) from BlessEventEntity e " +
            "where e.familyId = :familyId and e.createdAt >= :weekStart group by e.userId")
    List<Object[]> sumBlessByUserSince(@Param("familyId") Long familyId,
                                       @Param("weekStart") LocalDateTime weekStart);

    long countByTypeAndTargetUserIdAndCreatedAtAfter(BlessEventEntity.Type type,
                                                     Long targetUserId,
                                                     LocalDateTime since);
}
