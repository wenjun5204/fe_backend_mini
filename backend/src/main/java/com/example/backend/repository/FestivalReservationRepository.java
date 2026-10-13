package com.example.backend.repository;

import com.example.backend.entity.FestivalReservationEntity;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface FestivalReservationRepository extends JpaRepository<FestivalReservationEntity, Long> {

    Optional<FestivalReservationEntity> findByUserIdAndTemplateKey(Long userId, String templateKey);

    List<FestivalReservationEntity> findByUserIdOrderByCreatedAtDesc(Long userId);

    void deleteByUserIdAndTemplateKey(Long userId, String templateKey);
}
