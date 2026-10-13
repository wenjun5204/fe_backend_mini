package com.example.backend.repository;

import com.example.backend.entity.DayReminderEntity;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DayReminderRepository extends JpaRepository<DayReminderEntity, Long> {

    Optional<DayReminderEntity> findByDayIdAndUserId(Long dayId, Long userId);

    /** 删除日子时清全部家人的提醒行(日子不存在则提醒停止) */
    void deleteByDayId(Long dayId);

    /** reminder-due 扫描:reminders 非空即已开启提醒 */
    List<DayReminderEntity> findByRemindersNot(String reminders);
}
