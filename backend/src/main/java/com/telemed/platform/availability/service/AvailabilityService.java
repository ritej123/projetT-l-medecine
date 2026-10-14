package com.telemed.platform.availability.service;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;

import com.telemed.platform.appointment.domain.Appointment;
import com.telemed.platform.appointment.domain.AppointmentStatus;
import com.telemed.platform.appointment.repository.AppointmentRepository;
import com.telemed.platform.availability.api.AvailabilityResponse;
import com.telemed.platform.availability.api.CreateAvailabilityRequest;
import com.telemed.platform.availability.domain.Availability;
import com.telemed.platform.availability.mapper.AvailabilityMapper;
import com.telemed.platform.availability.repository.AvailabilityRepository;
import com.telemed.platform.common.exception.ConflictException;
import com.telemed.platform.common.exception.InvalidRequestException;
import com.telemed.platform.common.exception.NotFoundException;
import com.telemed.platform.doctor.domain.Doctor;
import com.telemed.platform.doctor.repository.DoctorRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AvailabilityService {

    private final DoctorRepository doctorRepository;
    private final AvailabilityRepository availabilityRepository;
    private final AvailabilityMapper availabilityMapper;
    private final AppointmentRepository appointmentRepository;
    private final Clock clock;
    private final ZoneId appointmentZone;

    public AvailabilityService(
            DoctorRepository doctorRepository,
            AvailabilityRepository availabilityRepository,
            AvailabilityMapper availabilityMapper,
            AppointmentRepository appointmentRepository,
            Clock clock,
            ZoneId appointmentZone) {
        this.doctorRepository = doctorRepository;
        this.availabilityRepository = availabilityRepository;
        this.availabilityMapper = availabilityMapper;
        this.appointmentRepository = appointmentRepository;
        this.clock = clock;
        this.appointmentZone = appointmentZone;
    }

    @Transactional
    public AvailabilityResponse create(String email, CreateAvailabilityRequest request) {
        validate(request);
        Doctor doctor = doctorRepository.findByUserEmail(email)
                .orElseThrow(() -> new NotFoundException("Profil médecin introuvable."));
        boolean overlaps = availabilityRepository.existsByDoctorIdAndDayOfWeekAndStartTimeLessThanAndEndTimeGreaterThan(
                doctor.getId(), request.dayOfWeek(), request.endTime(), request.startTime());
        if (overlaps) {
            throw new ConflictException("Cette plage horaire chevauche une disponibilité existante.");
        }
        Availability availability = new Availability(
                doctor, request.dayOfWeek(), request.startTime(), request.endTime(), request.slotDurationMinutes());
        return availabilityMapper.toResponse(availabilityRepository.save(availability));
    }

    @Transactional
    public AvailabilityResponse update(String email, UUID availabilityId, CreateAvailabilityRequest request) {
        validate(request);
        Doctor doctor = findDoctor(email);
        Availability availability = findOwnedAvailability(doctor.getId(), availabilityId);
        boolean overlaps = availabilityRepository
                .findAllByDoctorIdAndDayOfWeekOrderByStartTimeAsc(doctor.getId(), request.dayOfWeek()).stream()
                .filter(other -> !other.getId().equals(availabilityId))
                .anyMatch(other -> other.getStartTime().isBefore(request.endTime())
                        && other.getEndTime().isAfter(request.startTime()));
        if (overlaps) {
            throw new ConflictException("Cette plage horaire chevauche une disponibilité existante.");
        }
        if (hasIncompatibleAppointments(doctor.getId(), availability, request)) {
            throw new ConflictException(
                    "Cette modification affecterait un rendez-vous déjà réservé. Replanifiez-le avant de modifier la plage.");
        }
        availability.update(
                request.dayOfWeek(), request.startTime(), request.endTime(), request.slotDurationMinutes());
        return availabilityMapper.toResponse(availabilityRepository.save(availability));
    }

    @Transactional
    public void delete(String email, UUID availabilityId) {
        Doctor doctor = findDoctor(email);
        Availability availability = findOwnedAvailability(doctor.getId(), availabilityId);
        LocalDateTime now = LocalDateTime.now(clock.withZone(appointmentZone));
        List<Appointment> upcoming = appointmentRepository
                .findAllByDoctorIdAndScheduledAtGreaterThanEqualAndScheduledAtLessThanAndStatusInOrderByScheduledAtAsc(
                        doctor.getId(), now, now.plusYears(1), List.of(AppointmentStatus.PENDING, AppointmentStatus.CONFIRMED));
        boolean hasBookings = upcoming.stream().anyMatch(appointment -> {
            LocalDateTime scheduledAt = appointment.getScheduledAt();
            LocalTime start = scheduledAt.toLocalTime();
            return scheduledAt.getDayOfWeek() == availability.getDayOfWeek()
                    && !start.isBefore(availability.getStartTime())
                    && start.isBefore(availability.getEndTime());
        });
        if (hasBookings) {
            throw new ConflictException(
                    "Cette plage contient des rendez-vous réservés. Replanifiez-les avant de la supprimer.");
        }
        availabilityRepository.delete(availability);
    }

    @Transactional(readOnly = true)
    public List<AvailabilityResponse> findMine(String email) {
        Doctor doctor = findDoctor(email);
        return availabilityRepository.findAllByDoctorIdOrderByDayOfWeekAscStartTimeAsc(doctor.getId()).stream()
                .map(availabilityMapper::toResponse)
                .toList();
    }

    private Doctor findDoctor(String email) {
        return doctorRepository.findByUserEmail(email)
                .orElseThrow(() -> new NotFoundException("Profil médecin introuvable."));
    }

    private Availability findOwnedAvailability(UUID doctorId, UUID availabilityId) {
        return availabilityRepository.findById(availabilityId)
                .filter(item -> item.getDoctor().getId().equals(doctorId))
                .orElseThrow(() -> new NotFoundException("Disponibilité introuvable."));
    }

    private boolean hasIncompatibleAppointments(
            UUID doctorId, Availability current, CreateAvailabilityRequest request) {
        LocalDateTime now = LocalDateTime.now(clock.withZone(appointmentZone));
        List<Appointment> upcoming = appointmentRepository
                .findAllByDoctorIdAndScheduledAtGreaterThanEqualAndScheduledAtLessThanAndStatusInOrderByScheduledAtAsc(
                        doctorId, now, now.plusYears(1), List.of(AppointmentStatus.PENDING, AppointmentStatus.CONFIRMED));
        return upcoming.stream().filter(appointment -> {
            LocalDateTime scheduledAt = appointment.getScheduledAt();
            return scheduledAt.getDayOfWeek() == current.getDayOfWeek()
                    && !scheduledAt.toLocalTime().isBefore(current.getStartTime())
                    && scheduledAt.toLocalTime().isBefore(current.getEndTime());
        }).anyMatch(appointment -> {
            LocalDateTime scheduledAt = appointment.getScheduledAt();
            LocalTime start = scheduledAt.toLocalTime();
            long minutesFromStart = java.time.Duration.between(request.startTime(), start).toMinutes();
            return scheduledAt.getDayOfWeek() != request.dayOfWeek()
                    || start.isBefore(request.startTime())
                    || start.plusMinutes(appointment.getDurationMinutes()).isAfter(request.endTime())
                    || minutesFromStart < 0
                    || minutesFromStart % request.slotDurationMinutes() != 0;
        });
    }

    private void validate(CreateAvailabilityRequest request) {
        if (request == null
                || request.dayOfWeek() == null
                || request.startTime() == null
                || request.endTime() == null
                || !request.startTime().isBefore(request.endTime())
                || request.startTime().getSecond() != 0
                || request.startTime().getNano() != 0
                || request.endTime().getSecond() != 0
                || request.endTime().getNano() != 0
                || request.slotDurationMinutes() < 5
                || request.slotDurationMinutes() > 240
                || java.time.Duration.between(request.startTime(), request.endTime()).toMinutes()
                                % request.slotDurationMinutes()
                        != 0) {
            throw new InvalidRequestException(
                    "La plage doit contenir un nombre entier de créneaux de 5 à 240 minutes.");
        }
    }
}
