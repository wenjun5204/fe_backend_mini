package com.example.backend.repository;

import com.example.backend.entity.PhotoAssetEntity;
import com.example.backend.entity.PhotoAssetEntity.PhotoStatus;
import java.time.LocalDateTime;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PhotoAssetRepository extends JpaRepository<PhotoAssetEntity, Long> {

    Optional<PhotoAssetEntity> findByPhotoId(String photoId);

    boolean existsByPhotoId(String photoId);

    /** 日限 20 张:实体表自身即额度账本(成功事件即行,COUNT 后 INSERT 的 TOCTOU 窗口对硬频控可接受) */
    long countByUserIdAndCreatedAtGreaterThanEqual(Long userId, LocalDateTime start);

    /** ORPHAN GC:48h 未绑定照片物理删行(多实例并发扫除幂等) */
    long deleteByStatusAndCreatedAtBefore(PhotoStatus status, LocalDateTime cutoff);
}
