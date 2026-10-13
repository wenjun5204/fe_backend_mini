package com.example.backend.repository;

import com.example.backend.entity.BirthdayReminderEntity;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BirthdayReminderRepository extends JpaRepository<BirthdayReminderEntity, Long> {
    Optional<BirthdayReminderEntity> findByBirthdayIdAndUserId(Long birthdayId, Long userId);
    void deleteByBirthdayId(Long birthdayId);
}
