package com.telemed.platform.consultation.repository;

import java.util.List;
import java.util.UUID;

import com.telemed.platform.consultation.domain.ConsultationRevision;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ConsultationRevisionRepository extends JpaRepository<ConsultationRevision, UUID> {
    List<ConsultationRevision> findAllByConsultationIdOrderByRevisionNumberDesc(UUID consultationId);
    int countByConsultationId(UUID consultationId);
}
