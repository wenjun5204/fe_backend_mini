package com.example.backend.repository;

import com.example.backend.entity.FortuneDrawRecordEntity;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface FortuneDrawRecordRepository extends JpaRepository<FortuneDrawRecordEntity, Long> {
    Optional<FortuneDrawRecordEntity> findByUserIdAndDate(Long userId, LocalDate date);
    List<FortuneDrawRecordEntity> findByFamilyIdAndDate(Long familyId, LocalDate date);
}
