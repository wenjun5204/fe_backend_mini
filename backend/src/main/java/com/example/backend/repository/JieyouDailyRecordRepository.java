package com.example.backend.repository;

import com.example.backend.entity.JieyouDailyRecordEntity;
import java.time.LocalDate;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface JieyouDailyRecordRepository extends JpaRepository<JieyouDailyRecordEntity, Long> {
    Optional<JieyouDailyRecordEntity> findByUserIdAndDate(Long userId, LocalDate date);
}
