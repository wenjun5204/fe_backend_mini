package com.example.backend.repository;

import com.example.backend.entity.LetterGenerateQuotaEntity;
import java.time.LocalDate;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface LetterGenerateQuotaRepository extends JpaRepository<LetterGenerateQuotaEntity, Long> {

    Optional<LetterGenerateQuotaEntity> findByUserIdAndQuotaDate(Long userId, LocalDate quotaDate);

    /** 原子自增(JPQL @Modifying):upsert 撞唯一约束/既有行的并发兜底 */
    @Modifying
    @Query("UPDATE LetterGenerateQuotaEntity q SET q.count = q.count + 1 "
           + "WHERE q.userId = :userId AND q.quotaDate = :quotaDate")
    int incrementCount(@Param("userId") Long userId, @Param("quotaDate") LocalDate quotaDate);
}
