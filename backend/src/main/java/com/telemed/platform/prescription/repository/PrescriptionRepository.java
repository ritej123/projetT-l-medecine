package com.telemed.platform.prescription.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.telemed.platform.prescription.domain.Prescription;
import com.telemed.platform.prescription.domain.PrescriptionStatus;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PrescriptionRepository extends JpaRepository<Prescription, UUID> {

    Optional<Prescription> findByAppointmentId(UUID appointmentId);

    Optional<Prescription> findByIdAndAppointmentPatientUserEmailIgnoreCaseAndStatus(
            UUID prescriptionId, String patientEmail, PrescriptionStatus status);

    List<Prescription> findAllByAppointmentPatientIdAndStatusOrderByAppointmentScheduledAtDesc(
            UUID patientId, PrescriptionStatus status);
}
