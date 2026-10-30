package com.example.backend.repository;

import com.example.backend.entity.DutyRemindEntity;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DutyRemindRepository extends JpaRepository<DutyRemindEntity, Long> {

    /** 用户级一条(uk 物理保证) */
    Optional<DutyRemindEntity> findByUserId(Long userId);
}
