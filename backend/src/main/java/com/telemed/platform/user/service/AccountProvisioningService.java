package com.telemed.platform.user.service;

import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.UUID;

import com.telemed.platform.common.exception.ConflictException;
import com.telemed.platform.common.exception.InvalidRequestException;
import com.telemed.platform.common.exception.NotFoundException;
import com.telemed.platform.doctor.api.DoctorRegistrationResponse;
import com.telemed.platform.doctor.api.DoctorResponse;
import com.telemed.platform.doctor.domain.Doctor;
import com.telemed.platform.doctor.domain.DoctorVerificationStatus;
import com.telemed.platform.doctor.mapper.DoctorMapper;
import com.telemed.platform.doctor.repository.DoctorRepository;
import com.telemed.platform.patient.api.PatientResponse;
import com.telemed.platform.patient.domain.Patient;
import com.telemed.platform.patient.mapper.PatientMapper;
import com.telemed.platform.patient.repository.PatientRepository;
import com.telemed.platform.user.api.CreateDoctorRequest;
import com.telemed.platform.user.api.CreatePatientRequest;
import com.telemed.platform.user.domain.Role;
import com.telemed.platform.user.domain.User;
import com.telemed.platform.user.repository.UserRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
public class AccountProvisioningService {

    private final UserRepository userRepository;
    private final DoctorRepository doctorRepository;
    private final PatientRepository patientRepository;
    private final DoctorMapper doctorMapper;
    private final PatientMapper patientMapper;
    private final PasswordEncoder passwordEncoder;

    public AccountProvisioningService(
            UserRepository userRepository,
            DoctorRepository doctorRepository,
            PatientRepository patientRepository,
            DoctorMapper doctorMapper,
            PatientMapper patientMapper,
            PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.doctorRepository = doctorRepository;
        this.patientRepository = patientRepository;
        this.doctorMapper = doctorMapper;
        this.patientMapper = patientMapper;
        this.passwordEncoder = passwordEncoder;
    }

    @Transactional
    public DoctorResponse createDoctor(CreateDoctorRequest request) {
        User user = createUser(request.email(), request.password(), Role.DOCTOR);
        Doctor doctor = doctorRepository.save(new Doctor(user, request.specialty().trim(), request.bio()));
        return doctorMapper.toResponse(doctor);
    }

    @Transactional
    public PatientResponse createPatient(CreatePatientRequest request) {
        User user = createUser(request.email(), request.password(), Role.PATIENT);
        Patient patient = patientRepository.save(new Patient(user, request.medicalRecord()));
        return patientMapper.toResponse(patient);
    }

    @Transactional
    public void registerPatient(String email, String password) {
        User user = createUser(email, password, Role.PATIENT);
        patientRepository.save(new Patient(user, null));
    }

    @Transactional
    public void registerDoctorApplication(CreateDoctorRequest request) {
        User user = createUser(request.email(), request.password(), Role.DOCTOR);
        user.disable();
        Doctor doctor = new Doctor(user, request.specialty().trim(), request.bio());
        doctor.setVerificationStatus(com.telemed.platform.doctor.domain.DoctorVerificationStatus.PENDING);
        doctorRepository.save(doctor);
    }

    @Transactional(readOnly = true)
    public java.util.List<DoctorRegistrationResponse> findPendingDoctorApplications() {
        return doctorRepository.findAllByVerificationStatusOrderByUserCreatedAtAsc(DoctorVerificationStatus.PENDING).stream()
                .map(doctor -> new DoctorRegistrationResponse(
                        doctor.getId(),
                        doctor.getUser().getEmail(),
                        doctor.getSpecialty(),
                        doctor.getBio()))
                .toList();
    }

    @Transactional
    public DoctorResponse approveDoctorApplication(UUID doctorId) {
        Doctor doctor = doctorRepository.findById(doctorId)
                .orElseThrow(() -> new NotFoundException("Demande médecin introuvable."));
        if (doctor.getVerificationStatus() != DoctorVerificationStatus.PENDING) {
            throw new InvalidRequestException("Seule une demande médecin en attente peut être approuvée.");
        }
        doctor.setVerificationStatus(DoctorVerificationStatus.VERIFIED);
        doctor.getUser().enable();
        return doctorMapper.toResponse(doctor);
    }

    private User createUser(String email, String password, Role role) {
        if (!StringUtils.hasText(email) || !StringUtils.hasText(password)) {
            throw new InvalidRequestException("L'adresse e-mail et le mot de passe sont requis.");
        }
        if (password.getBytes(StandardCharsets.UTF_8).length > 72) {
            throw new InvalidRequestException("Le mot de passe dépasse la taille maximale autorisée.");
        }
        String normalizedEmail = email.trim().toLowerCase(Locale.ROOT);
        if (userRepository.existsByEmail(normalizedEmail)) {
            throw new ConflictException("Un compte existe déjà pour cette adresse e-mail.");
        }
        return userRepository.save(new User(normalizedEmail, passwordEncoder.encode(password), role));
    }
}
