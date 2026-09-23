package com.example.backend.repository;

import com.example.backend.entity.NotifySendEntity;
import java.time.LocalDateTime;
import org.springframework.data.jpa.repository.JpaRepository;

public interface NotifySendRepository extends JpaRepository<NotifySendEntity, Long> {

    /** 用户当日(自零点起)已发送条数,频控口径 */
    long countByUserIdAndSentAtGreaterThanEqual(Long userId, LocalDateTime since);
}
