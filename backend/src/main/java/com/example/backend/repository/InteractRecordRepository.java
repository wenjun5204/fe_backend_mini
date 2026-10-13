package com.example.backend.repository;

import com.example.backend.entity.InteractRecordEntity;
import java.time.LocalDate;
import org.springframework.data.jpa.repository.JpaRepository;

public interface InteractRecordRepository extends JpaRepository<InteractRecordEntity, Long> {
    long countByUserIdAndTargetUserIdAndDateAndType(Long userId, Long targetUserId,
                                                    LocalDate date, InteractRecordEntity.Type type);
}
