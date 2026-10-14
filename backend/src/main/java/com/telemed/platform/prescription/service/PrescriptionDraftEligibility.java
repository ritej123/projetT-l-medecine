package com.telemed.platform.prescription.service;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneId;

import com.telemed.platform.appointment.domain.Appointment;
import com.telemed.platform.appointment.domain.AppointmentStatus;

final class PrescriptionDraftEligibility {

    private PrescriptionDraftEligibility() {
    }

    static boolean isAllowed(Appointment appointment, Clock clock, ZoneId zoneId) {
        if (appointment.getStatus() == AppointmentStatus.DONE) {
            return true;
        }
        if (appointment.getStatus() != AppointmentStatus.CONFIRMED) {
            return false;
        }
        LocalDateTime now = LocalDateTime.now(clock.withZone(zoneId));
        LocalDateTime start = appointment.getScheduledAt();
        return !now.isBefore(start) && now.isBefore(start.plusMinutes(appointment.getDurationMinutes()));
    }
}
