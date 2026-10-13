package com.example.backend.repository;

import com.example.backend.dto.BirthdayDtos.SubjectType;
import com.example.backend.entity.BirthdayRecordEntity;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BirthdayRecordRepository extends JpaRepository<BirthdayRecordEntity, Long> {
    List<BirthdayRecordEntity> findByFamilyIdOrderByIdDesc(Long familyId);
    long countByFamilyIdAndCreatedByAndSubjectType(Long familyId, Long createdBy, SubjectType subjectType);
}
