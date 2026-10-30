package com.example.backend.repository;

import com.example.backend.entity.DutyRuleEntity;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DutyRuleRepository extends JpaRepository<DutyRuleEntity, Long> {

    /** 用户级单条生效(uk 物理保证) */
    Optional<DutyRuleEntity> findByUserId(Long userId);
}
