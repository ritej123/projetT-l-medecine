package com.telemed.platform.user.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import java.util.Optional;
import java.util.UUID;

import com.telemed.platform.common.exception.ConflictException;
import com.telemed.platform.doctor.api.DoctorResponse;
import com.telemed.platform.doctor.api.DoctorRegistrationResponse;
import com.telemed.platform.doctor.domain.Doctor;
import com.telemed.platform.doctor.domain.DoctorVerificationStatus;
import com.telemed.platform.doctor.mapper.DoctorMapper;
import com.telemed.platform.doctor.repository.DoctorRepository;
import com.telemed.platform.patient.mapper.PatientMapper;
import com.telemed.platform.patient.domain.Patient;
import com.telemed.platform.patient.repository.PatientRepository;
import com.telemed.platform.user.api.CreateDoctorRequest;
import com.telemed.platform.user.domain.Role;
import com.telemed.platform.user.domain.User;
import com.telemed.platform.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

@ExtendWith(MockitoExtension.class)
class AccountProvisioningServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private DoctorRepository doctorRepository;

    @Mock
    private PatientRepository patientRepository;

    @Mock
    private DoctorMapper doctorMapper;

    @Mock
    private PatientMapper patientMapper;

    @Mock
    private PasswordEncoder passwordEncoder;

    private AccountProvisioningService service;

    @BeforeEach
    void setUp() {
        service = new AccountProvisioningService(
                userRepository, doctorRepository, patientRepository, doctorMapper, patientMapper, passwordEncoder);
    }

    @Test
    void createsDoctorAccountWithNormalizedEmailAndHashedPassword() {
        var request = new CreateDoctorRequest(
                " DOCTOR@example.test ", "long-enough-password", "Cardiologie", "Présentation");
        when(userRepository.existsByEmail("doctor@example.test")).thenReturn(false);
        when(passwordEncoder.encode(request.password())).thenReturn("bcrypt-hash");
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(doctorRepository.save(any(Doctor.class))).thenAnswer(invocation -> invocation.getArgument(0));
        DoctorResponse response = new DoctorResponse(null, "Cardiologie", "Présentation");
        when(doctorMapper.toResponse(any(Doctor.class))).thenReturn(response);

        assertThat(service.createDoctor(request)).isEqualTo(response);
        verify(passwordEncoder).encode(request.password());
        verify(doctorRepository).save(any(Doctor.class));
    }

    @Test
    void rejectsDuplicateEmail() {
        when(userRepository.existsByEmail("doctor@example.test")).thenReturn(true);

        assertThatThrownBy(() -> service.createDoctor(new CreateDoctorRequest(
                "doctor@example.test", "long-enough-password", "Cardiologie", null)))
                .isInstanceOf(ConflictException.class);
        verify(userRepository, never()).save(any(User.class));
        verify(passwordEncoder, never()).encode(any());
    }

    @Test
    void publicRegistrationCreatesOnlyAPatientProfileWithoutMedicalNotes() {
        when(userRepository.existsByEmail("newpatient@example.test")).thenReturn(false);
        when(passwordEncoder.encode("a-long-enough-password")).thenReturn("bcrypt-hash");
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(patientRepository.save(any(Patient.class))).thenAnswer(invocation -> invocation.getArgument(0));

        service.registerPatient(" NewPatient@example.test ", "a-long-enough-password");

        verify(userRepository).save(org.mockito.ArgumentMatchers.argThat(user ->
                user.getRole() == Role.PATIENT
                        && user.getEmail().equals("newpatient@example.test")));
        verify(patientRepository).save(org.mockito.ArgumentMatchers.argThat(patient ->
                patient.getMedicalRecord() == null));
    }

    @Test
    void publicDoctorRegistrationCreatesDisabledDoctorUntilAdminApproval() {
        var request = new CreateDoctorRequest(
                "doctor@example.test", "a-long-enough-password", " Cardiologie ", "Présentation");
        when(userRepository.existsByEmail(request.email())).thenReturn(false);
        when(passwordEncoder.encode(request.password())).thenReturn("bcrypt-hash");
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(doctorRepository.save(any(Doctor.class))).thenAnswer(invocation -> invocation.getArgument(0));

        service.registerDoctorApplication(request);

        verify(doctorRepository).save(org.mockito.ArgumentMatchers.argThat(doctor ->
                doctor.getUser().getRole() == Role.DOCTOR
                        && !doctor.getUser().isEnabled()
                        && doctor.getSpecialty().equals("Cardiologie")));
    }

    @Test
    void adminApprovalEnablesDoctorAccount() {
        UUID doctorId = UUID.randomUUID();
        User user = new User("doctor@example.test", "bcrypt-hash", Role.DOCTOR);
        user.disable();
        Doctor doctor = new Doctor(user, "Cardiologie", "Présentation");
        when(doctorRepository.findById(doctorId)).thenReturn(Optional.of(doctor));
        DoctorResponse response = new DoctorResponse(doctorId, "Cardiologie", "Présentation");
        when(doctorMapper.toResponse(doctor)).thenReturn(response);

        assertThat(service.approveDoctorApplication(doctorId)).isEqualTo(response);
        assertThat(user.isEnabled()).isTrue();
    }

    @Test
    void listsOnlyPendingDoctorApplicationsForAdministration() {
        User user = new User("pending@example.test", "bcrypt-hash", Role.DOCTOR);
        user.disable();
        Doctor doctor = new Doctor(user, "Cardiologie", "Présentation");
        doctor.setVerificationStatus(DoctorVerificationStatus.PENDING);
        when(doctorRepository.findAllByVerificationStatusOrderByUserCreatedAtAsc(DoctorVerificationStatus.PENDING))
                .thenReturn(java.util.List.of(doctor));

        assertThat(service.findPendingDoctorApplications())
                .containsExactly(new DoctorRegistrationResponse(null, "pending@example.test", "Cardiologie", "Présentation"));
    }
}
