package com.example.backend.repository;

import com.example.backend.entity.DutyShiftEntity;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DutyShiftRepository extends JpaRepository<DutyShiftEntity, Long> {

    /** 月历区间查询(按日期升序);下次值班 60 天扫描同用(取首个非 rest 由服务层判断) */
    List<DutyShiftEntity> findByUserIdAndDutyDateBetweenOrderByDutyDateAsc(Long userId, LocalDate from, LocalDate to);

    Optional<DutyShiftEntity> findByUserIdAndDutyDate(Long userId, LocalDate dutyDate);

    void deleteByUserIdAndDutyDate(Long userId, LocalDate dutyDate);
}
