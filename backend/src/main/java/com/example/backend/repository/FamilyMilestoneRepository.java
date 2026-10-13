package com.example.backend.repository;

import com.example.backend.entity.FamilyMilestoneEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface FamilyMilestoneRepository extends JpaRepository<FamilyMilestoneEntity, Long> {
    boolean existsByFamilyIdAndMilestone(Long familyId, String milestone);
}
