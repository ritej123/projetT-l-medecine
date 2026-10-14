package com.telemed.platform.appointment.repository;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

import com.telemed.platform.appointment.domain.AppointmentSlot;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;

public interface AppointmentSlotRepository extends JpaRepository<AppointmentSlot, UUID> {

    @Lock(LockModeType.OPTIMISTIC)
    Optional<AppointmentSlot> findByDoctorIdAndScheduledAt(UUID doctorId, LocalDateTime scheduledAt);
}
