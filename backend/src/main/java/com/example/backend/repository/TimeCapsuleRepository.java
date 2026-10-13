package com.example.backend.repository;

import com.example.backend.dto.CapsuleDtos.CapsuleStatus;
import com.example.backend.entity.TimeCapsuleEntity;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TimeCapsuleRepository extends JpaRepository<TimeCapsuleEntity, Long> {

    /** 我的胶囊:封存按开箱日升序在前 */
    List<TimeCapsuleEntity> findByUserIdAndStatusOrderByOpenDateAsc(Long userId, CapsuleStatus status);

    /** 我的胶囊:已开按 openedAt 降序排后 */
    List<TimeCapsuleEntity> findByUserIdAndStatusOrderByOpenedAtDesc(Long userId, CapsuleStatus status);
}
