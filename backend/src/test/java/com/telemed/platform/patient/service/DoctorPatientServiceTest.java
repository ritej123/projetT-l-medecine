package com.telemed.platform.patient.service;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.ZoneId;
import java.util.Optional;
import java.util.UUID;

import com.telemed.platform.appointment.domain.AppointmentStatus;
import com.telemed.platform.appointment.repository.AppointmentRepository;
import com.telemed.platform.common.exception.NotFoundException;
import com.telemed.platform.consultation.repository.ConsultationRepository;
import com.telemed.platform.doctor.domain.Doctor;
import com.telemed.platform.doctor.repository.DoctorRepository;
import com.telemed.platform.patient.domain.Patient;
import com.telemed.platform.patient.repository.PatientRepository;
import com.telemed.platform.user.domain.Role;
import com.telemed.platform.user.domain.User;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class DoctorPatientServiceTest {

    @Mock private DoctorRepository doctorRepository;
    @Mock private PatientRepository patientRepository;
    @Mock private AppointmentRepository appointmentRepository;
    @Mock private ConsultationRepository consultationRepository;

    @Test
    void doesNotExposePatientRecordWithoutAnAuthorizedCareRelationship() {
        UUID doctorId = UUID.randomUUID();
        UUID patientId = UUID.randomUUID();
        Doctor doctor = new Doctor(new User("doctor-a@example.test", "hash", Role.DOCTOR), "General", null);
        ReflectionTestUtils.setField(doctor, "id", doctorId);
        Patient patient = new Patient(new User("patient@example.test", "hash", Role.PATIENT), "sensitive record");
        ReflectionTestUtils.setField(patient, "id", patientId);
        when(doctorRepository.findByUserEmail("doctor-a@example.test")).thenReturn(Optional.of(doctor));
        when(patientRepository.findById(patientId)).thenReturn(Optional.of(patient));
        when(appointmentRepository.existsByDoctorIdAndPatientIdAndStatusIn(
                        doctorId, patientId, java.util.List.of(AppointmentStatus.CONFIRMED, AppointmentStatus.DONE)))
                .thenReturn(false);
        DoctorPatientService service = new DoctorPatientService(
                doctorRepository,
                patientRepository,
                appointmentRepository,
                consultationRepository,
                Clock.systemUTC(),
                ZoneId.of("Europe/Paris"));

        assertThatThrownBy(() -> service.findPatient("doctor-a@example.test", patientId))
                .isInstanceOf(NotFoundException.class);
        verify(appointmentRepository, never())
                .findAllByDoctorIdAndPatientIdAndStatusInOrderByScheduledAtDesc(
                        org.mockito.ArgumentMatchers.any(),
                        org.mockito.ArgumentMatchers.any(),
                        org.mockito.ArgumentMatchers.any());
    }
}
