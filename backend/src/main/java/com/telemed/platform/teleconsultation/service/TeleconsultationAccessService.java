package com.telemed.platform.teleconsultation.service;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.UUID;

import com.telemed.platform.appointment.domain.Appointment;
import com.telemed.platform.appointment.domain.AppointmentStatus;
import com.telemed.platform.appointment.repository.AppointmentRepository;
import com.telemed.platform.common.exception.NotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TeleconsultationAccessService {

    private final AppointmentRepository appointmentRepository;
    private final Clock clock;
    private final ZoneId appointmentZone;

    public TeleconsultationAccessService(
            AppointmentRepository appointmentRepository,
            Clock clock,
            ZoneId appointmentZone) {
        this.appointmentRepository = appointmentRepository;
        this.clock = clock;
        this.appointmentZone = appointmentZone;
    }

    @Transactional(readOnly = true)
    public void assertCanJoin(UUID appointmentId, String email) {
        Appointment appointment = appointmentRepository.findById(appointmentId)
                .orElseThrow(() -> new NotFoundException("Rendez-vous introuvable."));
        boolean isPatient = appointment.getPatient().getUser().getEmail().equalsIgnoreCase(email);
        boolean isDoctor = appointment.getDoctor().getUser().getEmail().equalsIgnoreCase(email);
        if (!isPatient && !isDoctor) {
            throw new TeleconsultationAccessDeniedException(
                    "Ce compte n’est pas associé à ce rendez-vous.");
        }
        if (appointment.getStatus() != AppointmentStatus.PENDING
                && appointment.getStatus() != AppointmentStatus.CONFIRMED) {
            throw new TeleconsultationAccessDeniedException(
                    "Le rendez-vous n’est ni en attente ni confirmé. Il doit être planifié avant de rejoindre la salle.");
        }
        LocalDateTime now = LocalDateTime.now(clock.withZone(appointmentZone));
        LocalDateTime startsAt = appointment.getScheduledAt();
        LocalDateTime endsAt = startsAt.plusMinutes(appointment.getDurationMinutes());
        if (now.isBefore(startsAt) || !now.isBefore(endsAt)) {
            throw new TeleconsultationAccessDeniedException(
                    "La salle est accessible uniquement pendant l’horaire confirmé du rendez-vous.");
        }
    }
}
