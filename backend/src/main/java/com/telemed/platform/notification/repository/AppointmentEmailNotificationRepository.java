package com.telemed.platform.notification.repository;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import com.telemed.platform.notification.domain.AppointmentEmailNotification;
import com.telemed.platform.notification.domain.NotificationStatus;
import com.telemed.platform.notification.domain.NotificationType;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AppointmentEmailNotificationRepository
        extends JpaRepository<AppointmentEmailNotification, UUID> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select notification from AppointmentEmailNotification notification
            where notification.status = :status and notification.nextAttemptAt <= :now
            order by notification.nextAttemptAt asc
            """)
    List<AppointmentEmailNotification> findDueForUpdate(
            @Param("status") NotificationStatus status,
            @Param("now") Instant now,
            Pageable pageable);

    List<AppointmentEmailNotification> findAllByAppointmentIdAndStatusAndTypeIn(
            UUID appointmentId, NotificationStatus status, List<NotificationType> types);

    long countByAppointmentId(UUID appointmentId);
}
