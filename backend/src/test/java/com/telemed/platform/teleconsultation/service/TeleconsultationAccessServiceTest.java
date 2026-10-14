package com.telemed.platform.teleconsultation.service;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Optional;
import java.util.UUID;

import com.telemed.platform.appointment.domain.Appointment;
import com.telemed.platform.appointment.domain.AppointmentStatus;
import com.telemed.platform.appointment.repository.AppointmentRepository;
import com.telemed.platform.doctor.domain.Doctor;
import com.telemed.platform.patient.domain.Patient;
import com.telemed.platform.user.domain.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;

class TeleconsultationAccessServiceTest {

    private static final ZoneId ZONE = ZoneId.of("Europe/Paris");
    private static final UUID APPOINTMENT_ID = UUID.randomUUID();
    private static final LocalDateTime START = LocalDateTime.of(2026, 10, 12, 12, 0);

    private final AppointmentRepository appointmentRepository = mock(AppointmentRepository.class);
    private final Appointment appointment = mock(Appointment.class);
    private final Patient patient = mock(Patient.class);
    private final Doctor doctor = mock(Doctor.class);
    private final User patientUser = mock(User.class);
    private final User doctorUser = mock(User.class);
    private final TeleconsultationAccessService service = new TeleconsultationAccessService(
            appointmentRepository,
            Clock.fixed(Instant.parse("2026-10-12T10:30:00Z"), ZONE),
            ZONE);

    @BeforeEach
    void setUp() {
        when(appointmentRepository.findById(APPOINTMENT_ID)).thenReturn(Optional.of(appointment));
        when(appointment.getPatient()).thenReturn(patient);
        when(appointment.getDoctor()).thenReturn(doctor);
        when(patient.getUser()).thenReturn(patientUser);
        when(doctor.getUser()).thenReturn(doctorUser);
        when(patientUser.getEmail()).thenReturn("patient@example.test");
        when(doctorUser.getEmail()).thenReturn("doctor@example.test");
        when(appointment.getScheduledAt()).thenReturn(START);
        when(appointment.getDurationMinutes()).thenReturn(60);
        when(appointment.getStatus()).thenReturn(AppointmentStatus.CONFIRMED);
    }

    @Test
    void allowsEitherParticipantDuringTheScheduledWindow() {
        assertThatCode(() -> service.assertCanJoin(APPOINTMENT_ID, "patient@example.test")).doesNotThrowAnyException();
        assertThatCode(() -> service.assertCanJoin(APPOINTMENT_ID, "doctor@example.test")).doesNotThrowAnyException();
    }

    @Test
    void deniesNonParticipants() {
        assertThatThrownBy(() -> service.assertCanJoin(APPOINTMENT_ID, "other@example.test"))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void deniesJoiningBeforeOrAfterTheAppointmentWindow() {
        when(appointmentRepository.findById(APPOINTMENT_ID)).thenReturn(Optional.of(appointment));
        TeleconsultationAccessService beforeStart = new TeleconsultationAccessService(
                appointmentRepository,
                Clock.fixed(Instant.parse("2026-10-12T09:59:59Z"), ZONE),
                ZONE);
        TeleconsultationAccessService afterEnd = new TeleconsultationAccessService(
                appointmentRepository,
                Clock.fixed(Instant.parse("2026-10-12T11:00:00Z"), ZONE),
                ZONE);

        assertThatThrownBy(() -> beforeStart.assertCanJoin(APPOINTMENT_ID, "patient@example.test"))
                .isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> afterEnd.assertCanJoin(APPOINTMENT_ID, "patient@example.test"))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void deniesCancelledAndCompletedAppointments() {
        when(appointment.getStatus()).thenReturn(AppointmentStatus.CANCELLED);
        assertThatThrownBy(() -> service.assertCanJoin(APPOINTMENT_ID, "patient@example.test"))
                .isInstanceOf(AccessDeniedException.class);
        when(appointment.getStatus()).thenReturn(AppointmentStatus.DONE);
        assertThatThrownBy(() -> service.assertCanJoin(APPOINTMENT_ID, "patient@example.test"))
                .isInstanceOf(AccessDeniedException.class);
    }
}
