package com.telemed.platform.appointment.service;

import java.time.LocalDateTime;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.Clock;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import com.telemed.platform.appointment.api.AvailableAppointmentSlotResponse;
import com.telemed.platform.appointment.api.AppointmentResponse;
import com.telemed.platform.appointment.api.CreateAppointmentRequest;
import com.telemed.platform.appointment.api.RescheduleAppointmentRequest;
import com.telemed.platform.appointment.domain.Appointment;
import com.telemed.platform.appointment.domain.AppointmentStatus;
import com.telemed.platform.appointment.domain.AppointmentSlot;
import com.telemed.platform.appointment.mapper.AppointmentMapper;
import com.telemed.platform.appointment.repository.AppointmentRepository;
import com.telemed.platform.appointment.repository.AppointmentSlotRepository;
import com.telemed.platform.availability.domain.Availability;
import com.telemed.platform.availability.repository.AvailabilityRepository;
import com.telemed.platform.availability.domain.AvailabilityException;
import com.telemed.platform.availability.repository.AvailabilityExceptionRepository;
import com.telemed.platform.common.exception.ConflictException;
import com.telemed.platform.common.exception.InvalidRequestException;
import com.telemed.platform.common.exception.NotFoundException;
import com.telemed.platform.doctor.domain.Doctor;
import com.telemed.platform.doctor.repository.DoctorRepository;
import com.telemed.platform.patient.domain.Patient;
import com.telemed.platform.patient.repository.PatientRepository;
import com.telemed.platform.notification.service.AppointmentNotificationService;
import com.telemed.platform.notification.service.DoctorNotificationService;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AppointmentService {

    private static final List<AppointmentStatus> ACTIVE_STATUSES =
            List.of(AppointmentStatus.PENDING, AppointmentStatus.CONFIRMED);
    private static final int MAX_SLOT_DURATION_MINUTES = 240;

    private final PatientRepository patientRepository;
    private final DoctorRepository doctorRepository;
    private final AvailabilityRepository availabilityRepository;
    private final AvailabilityExceptionRepository availabilityExceptionRepository;
    private final AppointmentRepository appointmentRepository;
    private final AppointmentMapper appointmentMapper;
    private final AppointmentSlotRepository appointmentSlotRepository;
    private final AppointmentNotificationService notificationService;
    private final DoctorNotificationService doctorNotificationService;
    private final EntityManager entityManager;
    private final Clock clock;
    private final ZoneId appointmentZone;

    public AppointmentService(
            PatientRepository patientRepository,
            DoctorRepository doctorRepository,
            AvailabilityRepository availabilityRepository,
            AvailabilityExceptionRepository availabilityExceptionRepository,
            AppointmentRepository appointmentRepository,
            AppointmentMapper appointmentMapper,
            AppointmentSlotRepository appointmentSlotRepository,
            AppointmentNotificationService notificationService,
            DoctorNotificationService doctorNotificationService,
            EntityManager entityManager,
            Clock clock,
            ZoneId appointmentZone) {
        this.patientRepository = patientRepository;
        this.doctorRepository = doctorRepository;
        this.availabilityRepository = availabilityRepository;
        this.availabilityExceptionRepository = availabilityExceptionRepository;
        this.appointmentRepository = appointmentRepository;
        this.appointmentMapper = appointmentMapper;
        this.appointmentSlotRepository = appointmentSlotRepository;
        this.notificationService = notificationService;
        this.doctorNotificationService = doctorNotificationService;
        this.entityManager = entityManager;
        this.clock = clock;
        this.appointmentZone = appointmentZone;
    }

    @Transactional
    public AppointmentResponse book(String email, CreateAppointmentRequest request) {
        LocalDateTime scheduledAt = request.scheduledAt();
        if (!scheduledAt.isAfter(LocalDateTime.now(clock.withZone(appointmentZone)))
                || scheduledAt.getSecond() != 0
                || scheduledAt.getNano() != 0) {
            throw new InvalidRequestException("La date du rendez-vous doit être dans le futur.");
        }
        Patient patient = patientRepository.findByUserEmail(email)
                .orElseThrow(() -> new NotFoundException("Profil patient introuvable."));
        Doctor doctor = doctorRepository.findByIdAndUserEnabledTrueAndVerificationStatus(
                        request.doctorId(), com.telemed.platform.doctor.domain.DoctorVerificationStatus.VERIFIED)
                .orElseThrow(() -> new NotFoundException("Médecin introuvable."));
        LocalTime startTime = scheduledAt.toLocalTime();
        Availability availability = availabilityRepository
                .findAllByDoctorIdAndDayOfWeekOrderByStartTimeAsc(doctor.getId(), scheduledAt.getDayOfWeek())
                .stream()
                .filter(candidate -> isSlotWithinAvailability(candidate, startTime))
                .findFirst()
                .orElseThrow(() -> new ConflictException("Le créneau demandé n'est pas disponible."));
        if (availabilityExceptionRepository
                .findAllByDoctor_IdAndExceptionDateOrderByStartTimeAsc(doctor.getId(), scheduledAt.toLocalDate())
                .stream()
                .anyMatch(exception -> exceptionOverlaps(
                        scheduledAt.toLocalTime(),
                        scheduledAt.toLocalTime().plusMinutes(availability.getSlotDurationMinutes()),
                        exception))) {
            throw new ConflictException("Le créneau demandé est couvert par une indisponibilité exceptionnelle.");
        }
        entityManager.lock(availability, LockModeType.OPTIMISTIC);

        AppointmentSlot slot = appointmentSlotRepository.findByDoctorIdAndScheduledAt(doctor.getId(), scheduledAt)
                .orElseGet(() -> new AppointmentSlot(doctor, scheduledAt));
        if (slot.isReserved()) {
            throw new ConflictException("Le médecin n'est pas disponible à cette date.");
        }
        if (appointmentRepository.existsByDoctorIdAndScheduledAtAndStatusIn(
                doctor.getId(), scheduledAt, ACTIVE_STATUSES)) {
            throw new ConflictException("Ce créneau vient d'être réservé.");
        }
        slot.reserve();
        try {
            appointmentSlotRepository.saveAndFlush(slot);
            Appointment appointment = appointmentRepository.saveAndFlush(new Appointment(
                    patient, doctor, scheduledAt, availability.getSlotDurationMinutes()));
            notificationService.enqueueForBooking(appointment, patient.getUser().getEmail());
            enqueueDoctorEmailNotice(appointment);
            doctorNotificationService.appointmentCreated(appointment);
            return appointmentMapper.toResponse(appointment);
        } catch (DataIntegrityViolationException exception) {
            throw new ConflictException("Ce créneau vient d'être réservé.");
        }
    }

    @Transactional(readOnly = true)
    public List<AvailableAppointmentSlotResponse> findAvailableSlots(UUID doctorId, LocalDate from, LocalDate to) {
        if (from == null || to == null || to.isBefore(from) || to.isAfter(from.plusDays(30))) {
            throw new InvalidRequestException("La période doit être comprise entre 1 et 31 jours.");
        }
        Doctor doctor = doctorRepository.findByIdAndUserEnabledTrueAndVerificationStatus(
                        doctorId, com.telemed.platform.doctor.domain.DoctorVerificationStatus.VERIFIED)
                .orElseThrow(() -> new NotFoundException("Médecin introuvable."));
        LocalDateTime fromDateTime = from.atStartOfDay();
        LocalDateTime toDateTime = to.plusDays(1).atStartOfDay();
        List<Availability> schedules = availabilityRepository.findAllByDoctorIdOrderByDayOfWeekAscStartTimeAsc(
                doctor.getId());
        List<AvailabilityException> exceptions = availabilityExceptionRepository
                .findAllByDoctor_IdAndExceptionDateBetweenOrderByExceptionDateAscStartTimeAsc(
                        doctor.getId(), from, to);
        List<Appointment> bookedAppointments = appointmentRepository
                .findAllByDoctorIdAndScheduledAtGreaterThanEqualAndScheduledAtLessThanAndStatusInOrderByScheduledAtAsc(
                        doctor.getId(),
                        fromDateTime.minusMinutes(MAX_SLOT_DURATION_MINUTES),
                        toDateTime,
                        ACTIVE_STATUSES);
        List<AvailableAppointmentSlotResponse> slots = new ArrayList<>();
        LocalDateTime now = LocalDateTime.now(clock.withZone(appointmentZone));

        for (LocalDate date = from; !date.isAfter(to); date = date.plusDays(1)) {
            LocalDate slotDate = date;
            for (Availability availability : schedules) {
                if (availability.getDayOfWeek() != date.getDayOfWeek()) {
                    continue;
                }
                int duration = availability.getSlotDurationMinutes();
                for (LocalTime start = availability.getStartTime();
                        !start.plusMinutes(duration).isAfter(availability.getEndTime());
                        start = start.plusMinutes(duration)) {
                    LocalTime candidateStart = start;
                    LocalTime candidateEnd = start.plusMinutes(duration);
                    LocalDateTime slotStart = LocalDateTime.of(date, candidateStart);
                    LocalDateTime slotEnd = slotStart.plusMinutes(duration);
                    if (!slotStart.isAfter(now)
                            || exceptions.stream().filter(exception -> exception.getExceptionDate().equals(slotDate))
                                    .anyMatch(exception -> exceptionOverlaps(candidateStart, candidateEnd, exception))
                            || bookedAppointments.stream().anyMatch(appointment ->
                            overlaps(slotStart, slotEnd, appointment))) {
                        continue;
                    }
                    slots.add(new AvailableAppointmentSlotResponse(doctorId, slotStart, slotEnd));
                }
            }
        }
        return slots;
    }

    @Transactional(readOnly = true)
    public List<AppointmentResponse> findPatientAppointments(String email) {
        Patient patient = patientRepository.findByUserEmail(email)
                .orElseThrow(() -> new NotFoundException("Profil patient introuvable."));
        return appointmentRepository.findAllByPatientIdOrderByScheduledAtDesc(patient.getId()).stream()
                .map(appointmentMapper::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<AppointmentResponse> findDoctorAppointments(String email) {
        Doctor doctor = doctorRepository.findByUserEmail(email)
                .orElseThrow(() -> new NotFoundException("Profil médecin introuvable."));
        return appointmentRepository.findAllByDoctorIdOrderByScheduledAtAsc(doctor.getId()).stream()
                .map(appointmentMapper::toResponse)
                .toList();
    }

    @Transactional
    public AppointmentResponse updateStatus(String email, UUID appointmentId, AppointmentStatus nextStatus) {
        Appointment appointment = appointmentRepository.findById(appointmentId)
                .orElseThrow(() -> new NotFoundException("Rendez-vous introuvable."));
        if (!appointment.getDoctor().getUser().getEmail().equals(email)) {
            throw new NotFoundException("Rendez-vous introuvable.");
        }
        AppointmentStatus current = appointment.getStatus();
        boolean validTransition = current == AppointmentStatus.PENDING
                && (nextStatus == AppointmentStatus.CONFIRMED || nextStatus == AppointmentStatus.CANCELLED)
                || current == AppointmentStatus.CONFIRMED
                        && (nextStatus == AppointmentStatus.DONE || nextStatus == AppointmentStatus.CANCELLED);
        if (!validTransition) {
            throw new InvalidRequestException("Cette transition de statut n'est pas autorisée.");
        }
        if (nextStatus == AppointmentStatus.DONE
                && appointment.getScheduledAt().isAfter(LocalDateTime.now(clock.withZone(appointmentZone)))) {
            throw new InvalidRequestException("Un rendez-vous ne peut être terminé qu'après son horaire prévu.");
        }
        appointment.setStatus(nextStatus);
        doctorNotificationService.appointmentChanged(appointment, nextStatus == AppointmentStatus.CANCELLED);
        if (nextStatus == AppointmentStatus.CANCELLED) {
            enqueueDoctorEmailNotice(appointment);
        }
        if (nextStatus == AppointmentStatus.CANCELLED) {
            releaseSlot(appointment);
        }
        if (nextStatus == AppointmentStatus.CANCELLED || nextStatus == AppointmentStatus.DONE) {
            notificationService.cancelPendingNotifications(appointment.getId());
        }
        return appointmentMapper.toResponse(appointment);
    }

    @Transactional
    public AppointmentResponse reschedule(String email, UUID appointmentId, RescheduleAppointmentRequest request) {
        Appointment appointment = appointmentRepository.findById(appointmentId)
                .filter(candidate -> candidate.getDoctor().getUser().getEmail().equalsIgnoreCase(email))
                .orElseThrow(() -> new NotFoundException("Rendez-vous introuvable."));
        if (appointment.getStatus() != AppointmentStatus.PENDING
                && appointment.getStatus() != AppointmentStatus.CONFIRMED) {
            throw new InvalidRequestException("Seuls les rendez-vous en attente ou confirmés peuvent être replanifiés.");
        }
        LocalDateTime scheduledAt = request.scheduledAt();
        LocalDateTime now = LocalDateTime.now(clock.withZone(appointmentZone));
        if (scheduledAt == null
                || !scheduledAt.isAfter(now)
                || scheduledAt.getSecond() != 0
                || scheduledAt.getNano() != 0
                || scheduledAt.equals(appointment.getScheduledAt())) {
            throw new InvalidRequestException("Le nouvel horaire doit être différent et situé dans le futur.");
        }
        Doctor doctor = appointment.getDoctor();
        Availability availability = availabilityRepository
                .findAllByDoctorIdAndDayOfWeekOrderByStartTimeAsc(doctor.getId(), scheduledAt.getDayOfWeek())
                .stream()
                .filter(candidate -> isSlotWithinAvailability(candidate, scheduledAt.toLocalTime()))
                .findFirst()
                .orElseThrow(() -> new ConflictException("Le nouvel horaire n'est pas dans les disponibilités du médecin."));
        entityManager.lock(availability, LockModeType.OPTIMISTIC);
        int durationMinutes = availability.getSlotDurationMinutes();
        LocalTime startTime = scheduledAt.toLocalTime();
        if (availabilityExceptionRepository
                .findAllByDoctor_IdAndExceptionDateOrderByStartTimeAsc(doctor.getId(), scheduledAt.toLocalDate())
                .stream()
                .anyMatch(exception -> exceptionOverlaps(
                        startTime, startTime.plusMinutes(durationMinutes), exception))) {
            throw new ConflictException("Le nouvel horaire est couvert par une indisponibilité exceptionnelle.");
        }
        LocalDateTime newEnd = scheduledAt.plusMinutes(durationMinutes);
        boolean appointmentOverlap = appointmentRepository
                .findAllByDoctorIdAndScheduledAtGreaterThanEqualAndScheduledAtLessThanAndStatusInOrderByScheduledAtAsc(
                        doctor.getId(),
                        scheduledAt.minusMinutes(MAX_SLOT_DURATION_MINUTES),
                        newEnd,
                        ACTIVE_STATUSES)
                .stream()
                .filter(other -> !other.getId().equals(appointmentId))
                .anyMatch(other -> overlaps(scheduledAt, newEnd, other));
        if (appointmentOverlap) {
            throw new ConflictException("Le médecin est déjà occupé à ce nouvel horaire.");
        }
        AppointmentSlot newSlot = appointmentSlotRepository
                .findByDoctorIdAndScheduledAt(doctor.getId(), scheduledAt)
                .orElseGet(() -> new AppointmentSlot(doctor, scheduledAt));
        if (newSlot.isReserved()) {
            throw new ConflictException("Ce nouveau créneau vient d'être réservé.");
        }
        try {
            newSlot.reserve();
            appointmentSlotRepository.saveAndFlush(newSlot);
            releaseSlot(appointment);
            appointment.reschedule(scheduledAt, durationMinutes);
            notificationService.enqueueForReschedule(appointment, appointment.getPatient().getUser().getEmail());
            doctorNotificationService.appointmentChanged(appointment, false);
            return appointmentMapper.toResponse(appointment);
        } catch (DataIntegrityViolationException exception) {
            throw new ConflictException("Ce nouveau créneau vient d'être réservé.");
        }
    }

    @Transactional
    public AppointmentResponse cancelByPatient(String email, UUID appointmentId) {
        Appointment appointment = appointmentRepository.findById(appointmentId)
                .orElseThrow(() -> new NotFoundException("Rendez-vous introuvable."));
        if (!appointment.getPatient().getUser().getEmail().equals(email)) {
            throw new NotFoundException("Rendez-vous introuvable.");
        }
        if (appointment.getStatus() != AppointmentStatus.PENDING
                && appointment.getStatus() != AppointmentStatus.CONFIRMED) {
            throw new InvalidRequestException("Ce rendez-vous ne peut plus être annulé.");
        }
        appointment.setStatus(AppointmentStatus.CANCELLED);
        doctorNotificationService.appointmentChanged(appointment, true);
        enqueueDoctorEmailNotice(appointment);
        releaseSlot(appointment);
        notificationService.cancelPendingNotifications(appointment.getId());
        return appointmentMapper.toResponse(appointment);
    }

    private boolean isSlotWithinAvailability(Availability availability, LocalTime requestedStart) {
        int duration = availability.getSlotDurationMinutes();
        LocalTime requestedEnd = requestedStart.plusMinutes(duration);
        if (requestedStart.isBefore(availability.getStartTime())
                || requestedEnd.isAfter(availability.getEndTime())
                || availability.getStartTime().getSecond() != 0
                || availability.getStartTime().getNano() != 0
                || requestedStart.getSecond() != 0
                || requestedStart.getNano() != 0) {
            return false;
        }
        long minutesFromStart = Duration.between(availability.getStartTime(), requestedStart).toMinutes();
        return minutesFromStart % duration == 0;
    }

    private boolean overlaps(LocalDateTime slotStart, LocalDateTime slotEnd, Appointment appointment) {
        LocalDateTime appointmentEnd = appointment.getScheduledAt().plusMinutes(appointment.getDurationMinutes());
        return slotStart.isBefore(appointmentEnd) && slotEnd.isAfter(appointment.getScheduledAt());
    }

    private boolean exceptionOverlaps(
            LocalTime slotStart, LocalTime slotEnd, AvailabilityException exception) {
        return exception.getStartTime() == null
                || slotStart.isBefore(exception.getEndTime()) && slotEnd.isAfter(exception.getStartTime());
    }

    private void releaseSlot(Appointment appointment) {
        appointmentSlotRepository.findByDoctorIdAndScheduledAt(
                        appointment.getDoctor().getId(), appointment.getScheduledAt())
                .ifPresent(com.telemed.platform.appointment.domain.AppointmentSlot::release);
    }

    private void enqueueDoctorEmailNotice(Appointment appointment) {
        if (appointment.getDoctor().isEmailNotificationsEnabled()) {
            notificationService.enqueueDoctorNotice(
                    appointment, appointment.getDoctor().getUser().getEmail());
        }
    }
}
