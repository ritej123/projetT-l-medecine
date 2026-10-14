package com.telemed.platform.availability.service;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;

import com.telemed.platform.appointment.domain.Appointment;
import com.telemed.platform.appointment.domain.AppointmentStatus;
import com.telemed.platform.appointment.repository.AppointmentRepository;
import com.telemed.platform.availability.api.AvailabilityExceptionRequest;
import com.telemed.platform.availability.api.AvailabilityExceptionResponse;
import com.telemed.platform.availability.domain.AvailabilityException;
import com.telemed.platform.availability.repository.AvailabilityExceptionRepository;
import com.telemed.platform.common.exception.ConflictException;
import com.telemed.platform.common.exception.InvalidRequestException;
import com.telemed.platform.common.exception.NotFoundException;
import com.telemed.platform.doctor.domain.Doctor;
import com.telemed.platform.doctor.repository.DoctorRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AvailabilityExceptionService {

    private static final List<AppointmentStatus> ACTIVE =
            List.of(AppointmentStatus.PENDING, AppointmentStatus.CONFIRMED);

    private final DoctorRepository doctorRepository;
    private final AvailabilityExceptionRepository exceptionRepository;
    private final AppointmentRepository appointmentRepository;
    private final Clock clock;
    private final ZoneId appointmentZone;

    public AvailabilityExceptionService(
            DoctorRepository doctorRepository,
            AvailabilityExceptionRepository exceptionRepository,
            AppointmentRepository appointmentRepository,
            Clock clock,
            ZoneId appointmentZone) {
        this.doctorRepository = doctorRepository;
        this.exceptionRepository = exceptionRepository;
        this.appointmentRepository = appointmentRepository;
        this.clock = clock;
        this.appointmentZone = appointmentZone;
    }

    @Transactional(readOnly = true)
    public List<AvailabilityExceptionResponse> findMine(String email, LocalDate from, LocalDate to) {
        if (from == null || to == null || to.isBefore(from) || to.isAfter(from.plusMonths(12))) {
            throw new InvalidRequestException("La période des indisponibilités demandée est invalide.");
        }
        UUID doctorId = findDoctor(email).getId();
        return exceptionRepository
                .findAllByDoctor_IdAndExceptionDateBetweenOrderByExceptionDateAscStartTimeAsc(doctorId, from, to)
                .stream().map(AvailabilityExceptionService::toResponse).toList();
    }

    @Transactional
    public AvailabilityExceptionResponse create(String email, AvailabilityExceptionRequest request) {
        if (request.date() == null
                || (request.startTime() == null) != (request.endTime() == null)
                || request.startTime() != null && !request.startTime().isBefore(request.endTime())
                || request.date().isBefore(LocalDate.now(clock.withZone(appointmentZone)))) {
            throw new InvalidRequestException("La période d’indisponibilité est invalide.");
        }
        Doctor doctor = findDoctor(email);
        boolean overlaps = exceptionRepository
                .findAllByDoctor_IdAndExceptionDateOrderByStartTimeAsc(doctor.getId(), request.date()).stream()
                .anyMatch(existing -> overlaps(request.startTime(), request.endTime(),
                        existing.getStartTime(), existing.getEndTime()));
        if (overlaps) {
            throw new ConflictException("Cette période chevauche une indisponibilité déjà enregistrée.");
        }
        LocalDateTime start = request.date().atStartOfDay();
        LocalDateTime end = request.date().plusDays(1).atStartOfDay();
        List<Appointment> appointments = appointmentRepository
                .findAllByDoctorIdAndScheduledAtGreaterThanEqualAndScheduledAtLessThanAndStatusInOrderByScheduledAtAsc(
                        doctor.getId(), start, end, ACTIVE);
        boolean booked = appointments.stream().anyMatch(appointment -> {
            LocalDateTime appointmentEnd = appointment.getScheduledAt().plusMinutes(appointment.getDurationMinutes());
            return request.startTime() == null
                    || overlaps(request.startTime(), request.endTime(),
                            appointment.getScheduledAt().toLocalTime(), appointmentEnd.toLocalTime());
        });
        if (booked) {
            throw new ConflictException(
                    "Cette période contient un rendez-vous réservé. Replanifiez-le avant de créer l’indisponibilité.");
        }
        return toResponse(exceptionRepository.save(new AvailabilityException(
                doctor, request.date(), request.startTime(), request.endTime(),
                request.reason() == null ? null : request.reason().trim(), clock.instant())));
    }

    @Transactional
    public void delete(String email, UUID exceptionId) {
        UUID doctorId = findDoctor(email).getId();
        AvailabilityException exception = exceptionRepository.findByIdAndDoctor_Id(exceptionId, doctorId)
                .orElseThrow(() -> new NotFoundException("Indisponibilité introuvable."));
        exceptionRepository.delete(exception);
    }

    private Doctor findDoctor(String email) {
        return doctorRepository.findByUserEmail(email)
                .orElseThrow(() -> new NotFoundException("Profil médecin introuvable."));
    }

    private static boolean overlaps(
            java.time.LocalTime start, java.time.LocalTime end,
            java.time.LocalTime otherStart, java.time.LocalTime otherEnd) {
        return start == null || otherStart == null
                || start.isBefore(otherEnd) && end.isAfter(otherStart);
    }

    private static AvailabilityExceptionResponse toResponse(AvailabilityException exception) {
        return new AvailabilityExceptionResponse(
                exception.getId(), exception.getExceptionDate(), exception.getStartTime(),
                exception.getEndTime(), exception.getReason());
    }
}
