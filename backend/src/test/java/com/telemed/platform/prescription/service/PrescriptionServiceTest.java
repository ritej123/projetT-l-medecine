package com.telemed.platform.prescription.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.time.Clock;
import java.time.ZoneId;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

import com.telemed.platform.appointment.domain.Appointment;
import com.telemed.platform.appointment.domain.AppointmentStatus;
import com.telemed.platform.appointment.repository.AppointmentRepository;
import com.telemed.platform.patient.repository.PatientRepository;
import com.telemed.platform.prescription.api.PrescriptionResponse;
import com.telemed.platform.prescription.api.PatientPrescriptionResponse;
import com.telemed.platform.prescription.domain.Prescription;
import com.telemed.platform.prescription.domain.PrescriptionStatus;
import com.telemed.platform.prescription.mapper.PrescriptionMapper;
import com.telemed.platform.prescription.repository.PrescriptionRepository;
import com.telemed.platform.prescription.repository.PrescriptionRevisionRepository;
import com.telemed.platform.user.domain.Role;
import com.telemed.platform.user.domain.User;
import com.telemed.platform.doctor.domain.Doctor;
import com.telemed.platform.patient.domain.Patient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class PrescriptionServiceTest {

    @Mock
    private AppointmentRepository appointmentRepository;

    @Mock
    private PatientRepository patientRepository;

    @Mock
    private PrescriptionRepository prescriptionRepository;

    @Mock
    private PrescriptionRevisionRepository revisionRepository;

    @Mock
    private PrescriptionMapper prescriptionMapper;

    private PrescriptionService service;

    @BeforeEach
    void setUp() {
        service = new PrescriptionService(
                appointmentRepository,
                patientRepository,
                prescriptionRepository,
                revisionRepository,
                prescriptionMapper,
                Clock.systemUTC(),
                ZoneId.of("UTC"));
    }

    @Test
    void createsDraftForTheOwningDoctorAfterCompletedAppointment() {
        UUID appointmentId = UUID.randomUUID();
        Appointment appointment = new Appointment(
                new Patient(new User("patient@example.test", "hash", Role.PATIENT), null),
                new Doctor(new User("doctor@example.test", "hash", Role.DOCTOR), "General", null),
                LocalDateTime.now().minusHours(1), 30);
        appointment.setStatus(AppointmentStatus.DONE);
        PrescriptionResponse expected = new PrescriptionResponse(
                UUID.randomUUID(), appointmentId, "Confidential content", PrescriptionStatus.DRAFT, null,
                null, null, null, null);
        when(appointmentRepository.findById(appointmentId)).thenReturn(Optional.of(appointment));
        when(prescriptionRepository.findByAppointmentId(appointmentId)).thenReturn(Optional.empty());
        when(prescriptionRepository.save(any(Prescription.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(revisionRepository.findMaxRevisionNumber(any())).thenReturn(0);
        when(prescriptionMapper.toResponse(any(Prescription.class))).thenReturn(expected);

        assertThat(service.createDraft("doctor@example.test", appointmentId, "Confidential content"))
                .isEqualTo(expected);
        verify(prescriptionRepository).save(any(Prescription.class));
    }

    @Test
    void createsManualPrescriptionDraftDuringConfirmedAppointment() {
        UUID appointmentId = UUID.randomUUID();
        Appointment appointment = new Appointment(
                new Patient(new User("patient@example.test", "hash", Role.PATIENT), null),
                new Doctor(new User("doctor@example.test", "hash", Role.DOCTOR), "General", null),
                LocalDateTime.now(Clock.systemUTC()).minusMinutes(10), 30);
        appointment.setStatus(AppointmentStatus.CONFIRMED);
        PrescriptionResponse expected = new PrescriptionResponse(
                UUID.randomUUID(), appointmentId, "Prescription rédigée par le médecin",
                PrescriptionStatus.DRAFT, null, null, null, null, null);
        when(appointmentRepository.findById(appointmentId)).thenReturn(Optional.of(appointment));
        when(prescriptionRepository.findByAppointmentId(appointmentId)).thenReturn(Optional.empty());
        when(prescriptionRepository.save(any(Prescription.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(revisionRepository.findMaxRevisionNumber(any())).thenReturn(0);
        when(prescriptionMapper.toResponse(any(Prescription.class))).thenReturn(expected);

        assertThat(service.createDraft(
                "doctor@example.test", appointmentId, "Prescription rédigée par le médecin")).isEqualTo(expected);
        verify(prescriptionRepository).save(any(Prescription.class));
    }

    @Test
    void returnsEmptyWhenTheOwnedAppointmentHasNoPrescriptionYet() {
        UUID appointmentId = UUID.randomUUID();
        Appointment appointment = new Appointment(
                new Patient(new User("patient@example.test", "hash", Role.PATIENT), null),
                new Doctor(new User("doctor@example.test", "hash", Role.DOCTOR), "General", null),
                LocalDateTime.now(),
                30);
        ReflectionTestUtils.setField(appointment, "id", appointmentId);
        when(appointmentRepository.findById(appointmentId)).thenReturn(Optional.of(appointment));
        when(prescriptionRepository.findByAppointmentId(appointmentId)).thenReturn(Optional.empty());

        assertThat(service.findForDoctorAppointment("doctor@example.test", appointmentId)).isEmpty();
    }

    @Test
    void doesNotAllowManualPrescriptionDraftForPendingAppointment() {
        UUID appointmentId = UUID.randomUUID();
        Appointment appointment = new Appointment(
                new Patient(new User("patient@example.test", "hash", Role.PATIENT), null),
                new Doctor(new User("doctor@example.test", "hash", Role.DOCTOR), "General", null),
                LocalDateTime.now().minusMinutes(10), 30);
        when(appointmentRepository.findById(appointmentId)).thenReturn(Optional.of(appointment));

        assertThatThrownBy(() -> service.createDraft("doctor@example.test", appointmentId, "Prescription"))
                .isInstanceOf(com.telemed.platform.common.exception.InvalidRequestException.class);
    }

    @Test
    void doesNotAllowManualPrescriptionDraftOutsideConfirmedAppointmentWindow() {
        UUID appointmentId = UUID.randomUUID();
        Appointment appointment = new Appointment(
                new Patient(new User("patient@example.test", "hash", Role.PATIENT), null),
                new Doctor(new User("doctor@example.test", "hash", Role.DOCTOR), "General", null),
                LocalDateTime.now(Clock.systemUTC()).minusHours(2), 30);
        appointment.setStatus(AppointmentStatus.CONFIRMED);
        when(appointmentRepository.findById(appointmentId)).thenReturn(Optional.of(appointment));

        assertThatThrownBy(() -> service.createDraft("doctor@example.test", appointmentId, "Prescription"))
                .isInstanceOf(com.telemed.platform.common.exception.InvalidRequestException.class);
    }

    @Test
    void validatesDraftWithoutCreatingADigitalSignature() {
        service = new PrescriptionService(
                appointmentRepository,
                patientRepository,
                prescriptionRepository,
                revisionRepository,
                prescriptionMapper,
                Clock.fixed(Clock.systemUTC().instant(), ZoneId.of("UTC")),
                ZoneId.of("UTC"));
        User doctorUser = new User("doctor@example.test", "hash", Role.DOCTOR);
        ReflectionTestUtils.setField(doctorUser, "id", UUID.randomUUID());
        var appointment = new Appointment(
                new Patient(new User("patient@example.test", "hash", Role.PATIENT), null),
                new Doctor(doctorUser, "General", null),
                LocalDateTime.now().minusHours(1), 30);
        appointment.setStatus(AppointmentStatus.DONE);
        Prescription prescription = new Prescription(appointment, "Confidential content");
        UUID prescriptionId = UUID.randomUUID();
        ReflectionTestUtils.setField(prescription, "id", prescriptionId);
        when(prescriptionRepository.findById(prescriptionId)).thenReturn(Optional.of(prescription));
        when(revisionRepository.findMaxRevisionNumber(any())).thenReturn(0);
        when(prescriptionMapper.toResponse(prescription)).thenAnswer(invocation -> new PrescriptionResponse(
                prescriptionId, null, prescription.getContent(), prescription.getStatus(), prescription.getDate(),
                prescription.getSignedByUserId(), prescription.getSignedAt(),
                prescription.getContentSha256(), prescription.getDigitalSignature()));

        PrescriptionResponse response = service.validate("doctor@example.test", prescriptionId);

        assertThat(response.status()).isEqualTo(PrescriptionStatus.VALIDATED);
        assertThat(response.date()).isNotNull();
        assertThat(response.signedAt()).isNull();
        assertThat(response.signedByUserId()).isNull();
        assertThat(response.contentSha256()).isNull();
        assertThat(response.digitalSignature()).isNull();
    }

    @Test
    void keepsLegacyResignEndpointCompatibleWithoutAddingASignature() {
        User doctorUser = new User("doctor@example.test", "hash", Role.DOCTOR);
        var appointment = new Appointment(
                new Patient(new User("patient@example.test", "hash", Role.PATIENT), null),
                new Doctor(doctorUser, "General", null),
                LocalDateTime.now().minusHours(1), 30);
        UUID prescriptionId = UUID.randomUUID();
        Prescription prescription = new Prescription(appointment, "Contenu inchangé");
        ReflectionTestUtils.setField(prescription, "id", prescriptionId);
        prescription.validateOn(LocalDate.now(), UUID.randomUUID(), null, "legacy-hash", "legacy-signature");
        when(prescriptionRepository.findById(prescriptionId)).thenReturn(Optional.of(prescription));
        when(prescriptionMapper.toResponse(prescription)).thenAnswer(invocation -> new PrescriptionResponse(
                prescriptionId, null, prescription.getContent(), prescription.getStatus(), prescription.getDate(),
                prescription.getSignedByUserId(), prescription.getSignedAt(),
                prescription.getContentSha256(), prescription.getDigitalSignature()));

        PrescriptionResponse response = service.resign("doctor@example.test", prescriptionId);

        assertThat(response.status()).isEqualTo(PrescriptionStatus.VALIDATED);
        assertThat(response.digitalSignature()).isEqualTo("legacy-signature");
        verify(revisionRepository, org.mockito.Mockito.never()).save(any());
    }

    @Test
    void returnsValidatedPatientPrescriptionsWithTheirDoctorDetails() {
        UUID patientId = UUID.randomUUID();
        UUID doctorId = UUID.randomUUID();
        UUID prescriptionId = UUID.randomUUID();
        Patient patient = new Patient(new User("patient@example.test", "hash", Role.PATIENT), null);
        ReflectionTestUtils.setField(patient, "id", patientId);
        Doctor doctor = new Doctor(
                new User("doctor@example.test", "hash", Role.DOCTOR), "Cardiologie", null);
        ReflectionTestUtils.setField(doctor, "id", doctorId);
        doctor.updateProfile("Nadia", "Ben Salem", null, null, null, null, null, null, true);
        UUID appointmentId = UUID.randomUUID();
        Appointment appointment = new Appointment(
                patient, doctor, LocalDateTime.of(2026, 11, 1, 10, 0), 30);
        ReflectionTestUtils.setField(appointment, "id", appointmentId);
        Prescription prescription = new Prescription(appointment, "Traitement prescrit");
        ReflectionTestUtils.setField(prescription, "id", prescriptionId);
        prescription.validateOn(java.time.LocalDate.of(2026, 11, 1));
        when(patientRepository.findByUserEmail("patient@example.test")).thenReturn(Optional.of(patient));
        when(prescriptionRepository.findAllByAppointmentPatientIdAndStatusOrderByAppointmentScheduledAtDesc(
                patientId, PrescriptionStatus.VALIDATED)).thenReturn(java.util.List.of(prescription));

        assertThat(service.findPatientPrescriptions("patient@example.test"))
                .containsExactly(new PatientPrescriptionResponse(
                        prescriptionId,
                        appointmentId,
                        doctorId,
                        "Nadia Ben Salem",
                        "Cardiologie",
                        "Traitement prescrit",
                        java.time.LocalDate.of(2026, 11, 1),
                        LocalDateTime.of(2026, 11, 1, 10, 0)));
    }
}
