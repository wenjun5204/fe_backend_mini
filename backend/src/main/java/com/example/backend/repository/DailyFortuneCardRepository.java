package com.example.backend.repository;

import com.example.backend.entity.DailyFortuneCardEntity;
import java.time.LocalDate;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DailyFortuneCardRepository extends JpaRepository<DailyFortuneCardEntity, Long> {
    Optional<DailyFortuneCardEntity> findByFamilyIdAndDate(Long familyId, LocalDate date);
}
