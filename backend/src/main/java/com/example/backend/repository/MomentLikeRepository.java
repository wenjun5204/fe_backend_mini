package com.example.backend.repository;

import com.example.backend.entity.MomentLikeEntity;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface MomentLikeRepository extends JpaRepository<MomentLikeEntity, Long> {

    Optional<MomentLikeEntity> findByMomentIdAndUserId(Long momentId, Long userId);

    void deleteByMomentIdAndUserId(Long momentId, Long userId);

    long countByMomentId(Long momentId);

    /** 时间线批量计数(一条 group by 防 N+1);返回 [momentId, COUNT] */
    @Query("SELECT l.momentId, COUNT(l) FROM MomentLikeEntity l WHERE l.momentId IN :momentIds GROUP BY l.momentId")
    List<Object[]> countByMomentIdInGroupByMomentId(@Param("momentIds") Collection<Long> momentIds);

    /** 时间线批量取本人点赞态(likedByMe 聚合) */
    List<MomentLikeEntity> findByUserIdAndMomentIdIn(Long userId, Collection<Long> momentIds);
}
