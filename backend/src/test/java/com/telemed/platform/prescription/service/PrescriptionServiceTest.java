package com.telemed.platform.prescription.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.time.Clock;
import java.time.ZoneId;
import java.util.Optional;
import java.util.UUID;

import com.telemed.platform.appointment.domain.Appointment;
import com.telemed.platform.appointment.domain.AppointmentStatus;
import com.telemed.platform.appointment.repository.AppointmentRepository;
import com.telemed.platform.patient.repository.PatientRepository;
import com.telemed.platform.prescription.api.PrescriptionResponse;
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

    @Mock
    private PrescriptionSignatureService signatureService;

    private PrescriptionService service;

    @BeforeEach
    void setUp() {
        service = new PrescriptionService(
                appointmentRepository,
                patientRepository,
                prescriptionRepository,
                revisionRepository,
                prescriptionMapper,
                signatureService,
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
        when(revisionRepository.countByPrescriptionId(any())).thenReturn(0);
        when(prescriptionMapper.toResponse(any(Prescription.class))).thenReturn(expected);

        assertThat(service.createDraft("doctor@example.test", appointmentId, "Confidential content"))
                .isEqualTo(expected);
        verify(prescriptionRepository).save(any(Prescription.class));
    }

    @Test
    void validatesDraftAndSetsDate() {
        User doctorUser = new User("doctor@example.test", "hash", Role.DOCTOR);
        ReflectionTestUtils.setField(doctorUser, "id", UUID.randomUUID());
        var appointment = new Appointment(
                new Patient(new User("patient@example.test", "hash", Role.PATIENT), null),
                new Doctor(doctorUser, "General", null),
                LocalDateTime.now().minusHours(1), 30);
        Prescription prescription = new Prescription(appointment, "Confidential content");
        UUID prescriptionId = UUID.randomUUID();
        ReflectionTestUtils.setField(prescription, "id", prescriptionId);
        when(prescriptionRepository.findById(prescriptionId)).thenReturn(Optional.of(prescription));
        when(signatureService.sign(any(), any(), any(), any())).thenReturn("signature");
        when(revisionRepository.countByPrescriptionId(any())).thenReturn(0);
        when(prescriptionMapper.toResponse(prescription)).thenAnswer(invocation -> new PrescriptionResponse(
                prescriptionId, null, prescription.getContent(), prescription.getStatus(), prescription.getDate(),
                prescription.getSignedByUserId(), prescription.getSignedAt(),
                prescription.getContentSha256(), prescription.getDigitalSignature()));

        PrescriptionResponse response = service.validate("doctor@example.test", prescriptionId);

        assertThat(response.status()).isEqualTo(PrescriptionStatus.VALIDATED);
        assertThat(response.date()).isNotNull();
        assertThat(response.signedByUserId()).isNotNull();
        assertThat(response.digitalSignature()).isEqualTo("signature");
    }
}
