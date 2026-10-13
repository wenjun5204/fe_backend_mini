package com.example.backend.repository;

import com.example.backend.entity.VisitorUnlockEntity;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface VisitorUnlockRepository extends JpaRepository<VisitorUnlockEntity, Long> {

    Optional<VisitorUnlockEntity> findByCardIdAndDeviceId(Long cardId, String deviceId);

    /** 单卡累计收到的总心数(跨设备 SUM,与本人 totalHearts 统计同源) */
    @Query("SELECT COALESCE(SUM(v.hearts), 0) FROM VisitorUnlockEntity v WHERE v.cardId = :cardId")
    long sumHeartsByCard(@Param("cardId") Long cardId);

    /** 列表批量聚合(一条 group by 避免 N+1);返回 [cardId, SUM(hearts)] */
    @Query("SELECT v.cardId, SUM(v.hearts) FROM VisitorUnlockEntity v WHERE v.cardId IN :cardIds GROUP BY v.cardId")
    List<Object[]> sumHeartsByCardIds(@Param("cardIds") Collection<Long> cardIds);
}
