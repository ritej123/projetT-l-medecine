package com.telemed.platform.notification.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.telemed.platform.notification.domain.DoctorNotification;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DoctorNotificationRepository extends JpaRepository<DoctorNotification, UUID> {
    List<DoctorNotification> findAllByDoctorUserIdOrderByCreatedAtDesc(UUID doctorUserId, Pageable pageable);
    Optional<DoctorNotification> findByIdAndDoctorUserId(UUID id, UUID doctorUserId);
    long countByDoctorUserIdAndReadAtIsNull(UUID doctorUserId);
    boolean existsByAppointmentIdAndType(
            UUID appointmentId, com.telemed.platform.notification.domain.DoctorNotificationType type);
}
