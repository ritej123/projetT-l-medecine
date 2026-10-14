package com.telemed.platform.user.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.ZoneId;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

import com.telemed.platform.appointment.repository.AppointmentRepository;
import com.telemed.platform.common.exception.InvalidRequestException;
import com.telemed.platform.doctor.domain.Doctor;
import com.telemed.platform.doctor.domain.DoctorVerificationStatus;
import com.telemed.platform.doctor.repository.DoctorRepository;
import com.telemed.platform.patient.repository.PatientRepository;
import com.telemed.platform.user.domain.AdminAuditLog;
import com.telemed.platform.user.domain.Role;
import com.telemed.platform.user.domain.User;
import com.telemed.platform.user.repository.AdminAuditLogRepository;
import com.telemed.platform.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class AdminManagementServiceTest {

    @Mock
    private DoctorRepository doctorRepository;

    @Mock
    private PatientRepository patientRepository;

    @Mock
    private AppointmentRepository appointmentRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private AdminAuditLogRepository auditLogRepository;

    private AdminManagementService service;

    @BeforeEach
    void setUp() {
        service = new AdminManagementService(
                doctorRepository,
                patientRepository,
                appointmentRepository,
                userRepository,
                auditLogRepository,
                ZoneId.of("Europe/Paris"));
    }

    @Test
    void verifiesPendingDoctorAndRecordsAdministrativeAction() {
        UUID doctorId = UUID.randomUUID();
        User admin = new User("admin@example.test", "encoded-password", Role.ADMIN);
        User doctorUser = new User("doctor@example.test", "encoded-password", Role.DOCTOR);
        doctorUser.disable();
        Doctor doctor = new Doctor(doctorUser, "Cardiologie", "Présentation");
        ReflectionTestUtils.setField(doctor, "id", doctorId);
        when(userRepository.findByEmail(admin.getEmail())).thenReturn(Optional.of(admin));
        when(doctorRepository.findById(doctorId)).thenReturn(Optional.of(doctor));

        var response = service.verifyDoctor(doctorId, admin.getEmail());

        assertThat(response.verificationStatus()).isEqualTo(DoctorVerificationStatus.VERIFIED);
        assertThat(response.enabled()).isTrue();
        verify(auditLogRepository).save(any(AdminAuditLog.class));
    }

    @Test
    void doesNotAuditOrChangeDoctorWhenApplicationIsNotPending() {
        UUID doctorId = UUID.randomUUID();
        User admin = new User("admin@example.test", "encoded-password", Role.ADMIN);
        User doctorUser = new User("doctor@example.test", "encoded-password", Role.DOCTOR);
        Doctor doctor = new Doctor(doctorUser, "Cardiologie", "Présentation");
        when(userRepository.findByEmail(admin.getEmail())).thenReturn(Optional.of(admin));
        when(doctorRepository.findById(doctorId)).thenReturn(Optional.of(doctor));

        assertThatThrownBy(() -> service.verifyDoctor(doctorId, admin.getEmail()))
                .isInstanceOf(InvalidRequestException.class);

        assertThat(doctor.getVerificationStatus()).isEqualTo(DoctorVerificationStatus.VERIFIED);
        verify(auditLogRepository, never()).save(any(AdminAuditLog.class));
    }

    @Test
    void rejectsInvalidDateRangesBeforeQueryingRepositories() {
        assertThatThrownBy(() -> service.patients(
                        null, null, LocalDate.parse("2026-10-15"), LocalDate.parse("2026-10-14"), 0, 20))
                .isInstanceOf(InvalidRequestException.class);

        verify(patientRepository, never()).searchAdminPatients(
                any(), anyBoolean(), anyBoolean(), anyBoolean(), any(), anyBoolean(), any(), any());
    }

    @Test
    void rejectsUnsupportedPaginationBeforeQueryingRepositories() {
        assertThatThrownBy(() -> service.doctors(null, null, null, null, null, 0, 101))
                .isInstanceOf(InvalidRequestException.class);

        verify(doctorRepository, never()).searchAdminDoctors(
                any(), anyBoolean(), any(), anyBoolean(), anyBoolean(),
                anyBoolean(), any(), anyBoolean(), any(), any());
    }
}
