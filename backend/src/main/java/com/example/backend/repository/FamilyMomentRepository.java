package com.example.backend.repository;

import com.example.backend.entity.FamilyMomentEntity;
import com.example.backend.entity.FamilyMomentEntity.MomentStatus;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface FamilyMomentRepository extends JpaRepository<FamilyMomentEntity, Long> {

    /** 家族时间线:id 单调=createdAt 倒序,beforeId 游标取更早记录(缺省传 0 即全量首页);Pageable 取 limit+1 判 nextCursor */
    List<FamilyMomentEntity> findByFamilyIdAndStatusAndIdLessThanOrderByIdDesc(
            Long familyId, MomentStatus status, Long beforeId, Pageable pageable);

    /** 日限 10 条:含软删行(防「删了再发」洗额度);COUNT 后 INSERT 的 TOCTOU 窗口对硬频控可接受 */
    long countByUserIdAndCreatedAtGreaterThanEqual(Long userId, LocalDateTime start);
}
