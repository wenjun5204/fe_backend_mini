package com.example.backend.repository;

import com.example.backend.entity.MomentCollectEntity;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface MomentCollectRepository extends JpaRepository<MomentCollectEntity, Long> {

    Optional<MomentCollectEntity> findByMomentIdAndDayIdAndUserId(Long momentId, Long dayId, Long userId);

    void deleteByMomentIdAndDayIdAndUserId(Long momentId, Long dayId, Long userId);

    long countByMomentId(Long momentId);

    /** 时间线批量计数(一条 group by 防 N+1);返回 [momentId, COUNT] */
    @Query("SELECT c.momentId, COUNT(c) FROM MomentCollectEntity c WHERE c.momentId IN :momentIds GROUP BY c.momentId")
    List<Object[]> countByMomentIdInGroupByMomentId(@Param("momentIds") Collection<Long> momentIds);

    /** 时间线批量取本人收藏行(collectedByMeDayIds 聚合) */
    List<MomentCollectEntity> findByUserIdAndMomentIdIn(Long userId, Collection<Long> momentIds);

    /** 素材池:该日子被全家人收藏的瞬间(id 单调≈collectedAt 倒序;join moment 过滤 ACTIVE) */
    List<MomentCollectEntity> findByDayIdOrderByIdDesc(Long dayId);

    /** 福卡 momentIds 校验:是否已在 (momentId, dayId) 收藏素材池中 */
    boolean existsByMomentIdAndDayId(Long momentId, Long dayId);
}
