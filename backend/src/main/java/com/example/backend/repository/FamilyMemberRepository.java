package com.example.backend.repository;

import com.example.backend.entity.FamilyMemberEntity;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface FamilyMemberRepository extends JpaRepository<FamilyMemberEntity, Long> {
    Optional<FamilyMemberEntity> findByUserId(Long userId);
    List<FamilyMemberEntity> findByFamilyIdOrderByIdAsc(Long familyId);
    long countByFamilyId(Long familyId);
}
