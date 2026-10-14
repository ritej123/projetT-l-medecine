package com.telemed.platform.consultation.repository;

import java.util.Optional;
import java.util.List;
import java.util.UUID;

import com.telemed.platform.consultation.domain.Consultation;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ConsultationRepository extends JpaRepository<Consultation, UUID> {
    Optional<Consultation> findByAppointmentId(UUID appointmentId);
    List<Consultation> findAllByAppointmentIdIn(List<UUID> appointmentIds);
}
