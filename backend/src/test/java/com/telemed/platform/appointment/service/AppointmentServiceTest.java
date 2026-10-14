package com.telemed.platform.appointment.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.telemed.platform.appointment.api.AppointmentResponse;
import com.telemed.platform.appointment.api.CreateAppointmentRequest;
import com.telemed.platform.appointment.api.RescheduleAppointmentRequest;
import com.telemed.platform.appointment.domain.Appointment;
import com.telemed.platform.appointment.domain.AppointmentSlot;
import com.telemed.platform.appointment.domain.AppointmentStatus;
import com.telemed.platform.appointment.mapper.AppointmentMapper;
import com.telemed.platform.appointment.repository.AppointmentRepository;
import com.telemed.platform.appointment.repository.AppointmentSlotRepository;
import com.telemed.platform.availability.domain.Availability;
import com.telemed.platform.availability.repository.AvailabilityRepository;
import com.telemed.platform.availability.repository.AvailabilityExceptionRepository;
import com.telemed.platform.common.exception.ConflictException;
import com.telemed.platform.common.exception.InvalidRequestException;
import com.telemed.platform.doctor.domain.Doctor;
import com.telemed.platform.doctor.repository.DoctorRepository;
import com.telemed.platform.notification.service.AppointmentNotificationService;
import com.telemed.platform.notification.service.DoctorNotificationService;
import com.telemed.platform.patient.domain.Patient;
import com.telemed.platform.patient.repository.PatientRepository;
import com.telemed.platform.user.domain.Role;
import com.telemed.platform.user.domain.User;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class AppointmentServiceTest {

    private static final ZoneId ZONE = ZoneId.of("Europe/Paris");
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-10-12T08:00:00Z"), ZONE);

    @Mock
    private PatientRepository patientRepository;

    @Mock
    private DoctorRepository doctorRepository;

    @Mock
    private AvailabilityRepository availabilityRepository;

    @Mock
    private AvailabilityExceptionRepository availabilityExceptionRepository;

    @Mock
    private AppointmentRepository appointmentRepository;

    @Mock
    private AppointmentMapper appointmentMapper;

    @Mock
    private AppointmentSlotRepository appointmentSlotRepository;

    @Mock
    private AppointmentNotificationService notificationService;

    @Mock
    private DoctorNotificationService doctorNotificationService;

    @Mock
    private EntityManager entityManager;

    private AppointmentService service;

    @BeforeEach
    void setUp() {
        service = new AppointmentService(
                patientRepository,
                doctorRepository,
                availabilityRepository,
                availabilityExceptionRepository,
                appointmentRepository,
                appointmentMapper,
                appointmentSlotRepository,
                notificationService,
                doctorNotificationService,
                entityManager,
                CLOCK,
                ZONE);
    }

    @Test
    void returnsOnlyFreeSlotsAtTheDoctorsConfiguredDuration() {
        UUID doctorId = UUID.randomUUID();
        Doctor doctor = doctor("doctor@example.test", doctorId);
        when(doctorRepository.findByIdAndUserEnabledTrueAndVerificationStatus(
                        doctorId, com.telemed.platform.doctor.domain.DoctorVerificationStatus.VERIFIED))
                .thenReturn(Optional.of(doctor));
        when(availabilityRepository.findAllByDoctorIdOrderByDayOfWeekAscStartTimeAsc(doctorId))
                .thenReturn(List.of(new Availability(
                        doctor, DayOfWeek.TUESDAY, LocalTime.of(9, 0), LocalTime.of(10, 30), 30)));
        Appointment occupied = new Appointment(
                patient("existing@example.test"),
                doctor,
                LocalDateTime.of(2026, 10, 13, 9, 30),
                30);
        when(appointmentRepository
                        .findAllByDoctorIdAndScheduledAtGreaterThanEqualAndScheduledAtLessThanAndStatusInOrderByScheduledAtAsc(
                                any(), any(), any(), any()))
                .thenReturn(List.of(occupied));

        var slots = service.findAvailableSlots(
                doctorId, LocalDate.of(2026, 10, 13), LocalDate.of(2026, 10, 13));

        assertThat(slots).extracting(slot -> slot.startsAt()).containsExactly(
                LocalDateTime.of(2026, 10, 13, 9, 0),
                LocalDateTime.of(2026, 10, 13, 10, 0));
    }

    @Test
    void booksAFreeSlotAndEnqueuesItsConfirmation() {
        UUID doctorId = UUID.randomUUID();
        Doctor doctor = doctor("doctor@example.test", doctorId);
        Patient patient = patient("patient@example.test");
        LocalDateTime scheduledAt = LocalDateTime.of(2026, 10, 13, 10, 0);
        Availability availability =
                new Availability(doctor, DayOfWeek.TUESDAY, LocalTime.of(9, 0), LocalTime.of(12, 0), 30);
        when(patientRepository.findByUserEmail("patient@example.test")).thenReturn(Optional.of(patient));
        when(doctorRepository.findByIdAndUserEnabledTrueAndVerificationStatus(
                        doctorId, com.telemed.platform.doctor.domain.DoctorVerificationStatus.VERIFIED))
                .thenReturn(Optional.of(doctor));
        when(availabilityRepository.findAllByDoctorIdAndDayOfWeekOrderByStartTimeAsc(doctorId, DayOfWeek.TUESDAY))
                .thenReturn(List.of(availability));
        when(appointmentSlotRepository.findByDoctorIdAndScheduledAt(doctorId, scheduledAt))
                .thenReturn(Optional.empty());
        when(appointmentSlotRepository.saveAndFlush(any(AppointmentSlot.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(appointmentRepository.existsByDoctorIdAndScheduledAtAndStatusIn(any(), any(), any()))
                .thenReturn(false);
        when(appointmentRepository.saveAndFlush(any(Appointment.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        AppointmentResponse expected = new AppointmentResponse(
                UUID.randomUUID(), UUID.randomUUID(), doctorId, scheduledAt, 30, AppointmentStatus.PENDING);
        when(appointmentMapper.toResponse(any(Appointment.class))).thenReturn(expected);

        assertThat(service.book("patient@example.test", new CreateAppointmentRequest(doctorId, scheduledAt)))
                .isEqualTo(expected);
        verify(entityManager).lock(availability, jakarta.persistence.LockModeType.OPTIMISTIC);
        verify(appointmentSlotRepository).saveAndFlush(any(AppointmentSlot.class));
        verify(notificationService).enqueueForBooking(any(Appointment.class), any());
    }

    @Test
    void rejectsAConcurrentReservationOfAnAlreadyClaimedSlot() {
        UUID doctorId = UUID.randomUUID();
        Doctor doctor = doctor("doctor@example.test", doctorId);
        LocalDateTime scheduledAt = LocalDateTime.of(2026, 10, 13, 10, 0);
        AppointmentSlot reservedSlot = new AppointmentSlot(doctor, scheduledAt);
        reservedSlot.reserve();
        when(patientRepository.findByUserEmail("patient@example.test"))
                .thenReturn(Optional.of(patient("patient@example.test")));
        when(doctorRepository.findByIdAndUserEnabledTrueAndVerificationStatus(
                        doctorId, com.telemed.platform.doctor.domain.DoctorVerificationStatus.VERIFIED))
                .thenReturn(Optional.of(doctor));
        when(availabilityRepository.findAllByDoctorIdAndDayOfWeekOrderByStartTimeAsc(doctorId, DayOfWeek.TUESDAY))
                .thenReturn(List.of(new Availability(
                        doctor, DayOfWeek.TUESDAY, LocalTime.of(9, 0), LocalTime.of(12, 0), 30)));
        when(appointmentSlotRepository.findByDoctorIdAndScheduledAt(doctorId, scheduledAt))
                .thenReturn(Optional.of(reservedSlot));

        assertThatThrownBy(() -> service.book(
                "patient@example.test", new CreateAppointmentRequest(doctorId, scheduledAt)))
                .isInstanceOf(ConflictException.class);
        verify(appointmentRepository, never()).saveAndFlush(any(Appointment.class));
    }

    @Test
    void rejectsAvailabilityOutsideTheRequestedDateRange() {
        assertThatThrownBy(() -> service.findAvailableSlots(
                UUID.randomUUID(), LocalDate.of(2026, 10, 12), LocalDate.of(2026, 11, 12)))
                .isInstanceOf(InvalidRequestException.class);
    }

    @Test
    void cancellationReleasesTheSlotAndCancelsPendingEmails() {
        UUID appointmentId = UUID.randomUUID();
        UUID doctorId = UUID.randomUUID();
        UUID patientId = UUID.randomUUID();
        Doctor doctor = doctor("doctor@example.test", doctorId);
        Patient patient = patient("patient@example.test");
        ReflectionTestUtils.setField(patient, "id", patientId);
        LocalDateTime scheduledAt = LocalDateTime.of(2026, 10, 13, 10, 0);
        Appointment appointment = new Appointment(patient, doctor, scheduledAt, 30);
        ReflectionTestUtils.setField(appointment, "id", appointmentId);
        AppointmentSlot slot = new AppointmentSlot(doctor, scheduledAt);
        slot.reserve();
        when(appointmentRepository.findById(appointmentId)).thenReturn(Optional.of(appointment));
        when(appointmentSlotRepository.findByDoctorIdAndScheduledAt(doctorId, scheduledAt))
                .thenReturn(Optional.of(slot));

        service.cancelByPatient("patient@example.test", appointmentId);

        assertThat(slot.isReserved()).isFalse();
        verify(notificationService).cancelPendingNotifications(appointmentId);
    }

    @Test
    void reschedulesOnlyIntoAnOpenSlotAndNotifiesThePatient() {
        UUID appointmentId = UUID.randomUUID();
        UUID doctorId = UUID.randomUUID();
        Doctor doctor = doctor("doctor@example.test", doctorId);
        Patient patient = patient("patient@example.test");
        LocalDateTime oldTime = LocalDateTime.of(2026, 10, 13, 10, 0);
        LocalDateTime newTime = LocalDateTime.of(2026, 10, 14, 11, 0);
        Appointment appointment = new Appointment(patient, doctor, oldTime, 30);
        ReflectionTestUtils.setField(appointment, "id", appointmentId);
        AppointmentSlot oldSlot = new AppointmentSlot(doctor, oldTime);
        oldSlot.reserve();
        Availability availability =
                new Availability(doctor, DayOfWeek.WEDNESDAY, LocalTime.of(9, 0), LocalTime.of(12, 0), 30);
        when(appointmentRepository.findById(appointmentId)).thenReturn(Optional.of(appointment));
        when(availabilityRepository.findAllByDoctorIdAndDayOfWeekOrderByStartTimeAsc(
                        doctorId, DayOfWeek.WEDNESDAY))
                .thenReturn(List.of(availability));
        when(availabilityExceptionRepository.findAllByDoctor_IdAndExceptionDateOrderByStartTimeAsc(
                        doctorId, newTime.toLocalDate()))
                .thenReturn(List.of());
        when(appointmentRepository
                        .findAllByDoctorIdAndScheduledAtGreaterThanEqualAndScheduledAtLessThanAndStatusInOrderByScheduledAtAsc(
                                any(), any(), any(), any()))
                .thenReturn(List.of());
        when(appointmentSlotRepository.findByDoctorIdAndScheduledAt(doctorId, newTime))
                .thenReturn(Optional.empty());
        when(appointmentSlotRepository.findByDoctorIdAndScheduledAt(doctorId, oldTime))
                .thenReturn(Optional.of(oldSlot));
        when(appointmentSlotRepository.saveAndFlush(any(AppointmentSlot.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        AppointmentResponse expected = new AppointmentResponse(
                appointmentId, UUID.randomUUID(), doctorId, newTime, 30, AppointmentStatus.PENDING);
        when(appointmentMapper.toResponse(appointment)).thenReturn(expected);

        assertThat(service.reschedule(
                "doctor@example.test", appointmentId, new RescheduleAppointmentRequest(newTime)))
                .isEqualTo(expected);
        assertThat(appointment.getScheduledAt()).isEqualTo(newTime);
        assertThat(oldSlot.isReserved()).isFalse();
        verify(notificationService).enqueueForReschedule(appointment, "patient@example.test");
        verify(doctorNotificationService).appointmentChanged(appointment, false);
    }

    private Doctor doctor(String email, UUID id) {
        Doctor doctor = new Doctor(new User(email, "hash", Role.DOCTOR), "General", null);
        ReflectionTestUtils.setField(doctor, "id", id);
        return doctor;
    }

    private Patient patient(String email) {
        return new Patient(new User(email, "hash", Role.PATIENT), null);
    }
}
