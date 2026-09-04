package com.example.backend.repository;

import com.example.backend.dto.FortuneDtos;
import com.example.backend.entity.FamilyEntity;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface FamilyRepository extends JpaRepository<FamilyEntity, Long> {
    Optional<FamilyEntity> findByInviteCode(String inviteCode);
    List<FamilyEntity> findByPlateLevel(FortuneDtos.PlateLevel level);
    boolean existsByInviteCode(String inviteCode);
}
