package com.telemed.platform.user.service;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.DateTimeException;
import java.util.UUID;

import com.telemed.platform.appointment.api.AdminAppointmentResponse;
import com.telemed.platform.appointment.domain.AppointmentStatus;
import com.telemed.platform.appointment.repository.AppointmentRepository;
import com.telemed.platform.common.exception.InvalidRequestException;
import com.telemed.platform.common.exception.NotFoundException;
import com.telemed.platform.doctor.domain.Doctor;
import com.telemed.platform.doctor.domain.DoctorVerificationStatus;
import com.telemed.platform.doctor.repository.DoctorRepository;
import com.telemed.platform.patient.domain.Patient;
import com.telemed.platform.patient.repository.PatientRepository;
import com.telemed.platform.user.api.AdminDoctorResponse;
import com.telemed.platform.user.api.AdminAuditLogResponse;
import com.telemed.platform.user.api.AdminPatientResponse;
import com.telemed.platform.user.domain.AdminAuditLog;
import com.telemed.platform.user.domain.AdminAuditResult;
import com.telemed.platform.user.domain.Role;
import com.telemed.platform.user.domain.User;
import com.telemed.platform.user.repository.AdminAuditLogRepository;
import com.telemed.platform.user.repository.UserRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
public class AdminManagementService {

    private static final Instant MIN_INSTANT = LocalDate.of(1, 1, 1)
            .atStartOfDay(ZoneId.of("UTC"))
            .toInstant();
    private static final Instant MAX_INSTANT = LocalDate.of(10000, 1, 1)
            .atStartOfDay(ZoneId.of("UTC"))
            .toInstant();
    private static final LocalDateTime MIN_DATE_TIME = LocalDate.of(1, 1, 1).atStartOfDay();
    private static final LocalDateTime MAX_DATE_TIME = LocalDate.of(10000, 1, 1).atStartOfDay();

    private final DoctorRepository doctorRepository;
    private final PatientRepository patientRepository;
    private final AppointmentRepository appointmentRepository;
    private final UserRepository userRepository;
    private final AdminAuditLogRepository auditLogRepository;
    private final ZoneId appointmentZone;

    public AdminManagementService(
            DoctorRepository doctorRepository,
            PatientRepository patientRepository,
            AppointmentRepository appointmentRepository,
            UserRepository userRepository,
            AdminAuditLogRepository auditLogRepository,
            ZoneId appointmentZone) {
        this.doctorRepository = doctorRepository;
        this.patientRepository = patientRepository;
        this.appointmentRepository = appointmentRepository;
        this.userRepository = userRepository;
        this.auditLogRepository = auditLogRepository;
        this.appointmentZone = appointmentZone;
    }

    @Transactional(readOnly = true)
    public Page<AdminDoctorResponse> doctors(
            String search,
            DoctorVerificationStatus status,
            Boolean enabled,
            LocalDate from,
            LocalDate to,
            int page,
            int size) {
        validateDateRange(from, to);
        return doctorRepository.searchAdminDoctors(
                normalizeSearch(search),
                status != null,
                status == null ? DoctorVerificationStatus.PENDING : status,
                enabled != null,
                Boolean.TRUE.equals(enabled),
                from != null,
                from == null ? MIN_INSTANT : startOfDay(from),
                to != null,
                to == null ? MAX_INSTANT : startOfNextDay(to),
                pageRequest(page, size, "user.createdAt"));
    }

    @Transactional(readOnly = true)
    public Page<AdminPatientResponse> patients(
            String search, Boolean enabled, LocalDate from, LocalDate to, int page, int size) {
        validateDateRange(from, to);
        return patientRepository.searchAdminPatients(
                normalizeSearch(search),
                enabled != null,
                Boolean.TRUE.equals(enabled),
                from != null,
                from == null ? MIN_INSTANT : startOfDay(from),
                to != null,
                to == null ? MAX_INSTANT : startOfNextDay(to),
                pageRequest(page, size, "user.createdAt"));
    }

    @Transactional(readOnly = true)
    public Page<AdminAppointmentResponse> appointments(
            String search, AppointmentStatus status, LocalDate from, LocalDate to, int page, int size) {
        validateDateRange(from, to);
        return appointmentRepository.searchAdminAppointments(
                normalizeSearch(search),
                status != null,
                status == null ? AppointmentStatus.PENDING : status,
                from != null,
                from == null ? MIN_DATE_TIME : from.atStartOfDay(),
                to != null,
                to == null ? MAX_DATE_TIME : nextDay(to).atStartOfDay(),
                pageRequest(page, size, "scheduledAt"));
    }

    @Transactional(readOnly = true)
    public Page<AdminAuditLogResponse> auditLogs(int page, int size) {
        return userRepository.findAdminAuditLogs(pageRequest(page, size, "createdAt"));
    }

    @Transactional
    public AdminDoctorResponse verifyDoctor(UUID doctorId, String actorEmail) {
        User actor = requireAdmin(actorEmail);
        Doctor doctor = doctorRepository.findById(doctorId)
                .orElseThrow(() -> new NotFoundException("Médecin introuvable."));
        if (doctor.getVerificationStatus() != DoctorVerificationStatus.PENDING) {
            throw new InvalidRequestException("Seule une demande médecin en attente peut être validée.");
        }
        doctor.setVerificationStatus(DoctorVerificationStatus.VERIFIED);
        doctor.getUser().enable();
        record(actor, "DOCTOR_VERIFY", "DOCTOR", doctorId, AdminAuditResult.SUCCESS);
        return toDoctorResponse(doctor);
    }

    @Transactional
    public AdminDoctorResponse rejectDoctor(UUID doctorId, String actorEmail) {
        User actor = requireAdmin(actorEmail);
        Doctor doctor = doctorRepository.findById(doctorId)
                .orElseThrow(() -> new NotFoundException("Médecin introuvable."));
        if (doctor.getVerificationStatus() != DoctorVerificationStatus.PENDING) {
            throw new InvalidRequestException("Seule une demande médecin en attente peut être refusée.");
        }
        doctor.setVerificationStatus(DoctorVerificationStatus.REJECTED);
        doctor.getUser().disable();
        record(actor, "DOCTOR_REJECT", "DOCTOR", doctorId, AdminAuditResult.SUCCESS);
        return toDoctorResponse(doctor);
    }

    @Transactional
    public AdminPatientResponse setPatientStatus(UUID patientId, boolean enabled, String actorEmail) {
        User actor = requireAdmin(actorEmail);
        Patient patient = patientRepository.findById(patientId)
                .orElseThrow(() -> new NotFoundException("Patient introuvable."));
        patient.getUser().setEnabled(enabled);
        record(actor, enabled ? "PATIENT_REACTIVATE" : "PATIENT_SUSPEND", "PATIENT", patientId, AdminAuditResult.SUCCESS);
        return new AdminPatientResponse(
                patient.getId(), patient.getUser().getEmail(), patient.getUser().isEnabled(), patient.getUser().getCreatedAt());
    }

    @Transactional
    public AdminDoctorResponse setDoctorStatus(UUID doctorId, boolean enabled, String actorEmail) {
        User actor = requireAdmin(actorEmail);
        Doctor doctor = doctorRepository.findById(doctorId)
                .orElseThrow(() -> new NotFoundException("Médecin introuvable."));
        if (doctor.getVerificationStatus() != DoctorVerificationStatus.VERIFIED) {
            throw new InvalidRequestException("Seul un médecin vérifié peut être suspendu ou réactivé.");
        }
        doctor.getUser().setEnabled(enabled);
        record(actor, enabled ? "DOCTOR_REACTIVATE" : "DOCTOR_SUSPEND", "DOCTOR", doctorId, AdminAuditResult.SUCCESS);
        return toDoctorResponse(doctor);
    }

    private User requireAdmin(String email) {
        User actor = userRepository.findByEmail(email)
                .orElseThrow(() -> new AccessDeniedException("Compte administrateur requis."));
        if (actor.getRole() != Role.ADMIN || !actor.isEnabled()) {
            throw new AccessDeniedException("Compte administrateur requis.");
        }
        return actor;
    }

    private void record(User actor, String action, String targetType, UUID targetId, AdminAuditResult result) {
        auditLogRepository.save(new AdminAuditLog(actor, action, targetType, targetId, result));
    }

    private AdminDoctorResponse toDoctorResponse(Doctor doctor) {
        User user = doctor.getUser();
        return new AdminDoctorResponse(
                doctor.getId(),
                user.getEmail(),
                doctor.getSpecialty(),
                doctor.getBio(),
                doctor.getVerificationStatus(),
                user.isEnabled(),
                user.getCreatedAt());
    }

    private Pageable pageRequest(int page, int size, String sortField) {
        if (page < 0 || size < 1 || size > 100) {
            throw new InvalidRequestException("La pagination doit utiliser une page >= 0 et une taille entre 1 et 100.");
        }
        return PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, sortField));
    }

    private String normalizeSearch(String search) {
        return StringUtils.hasText(search) ? search.trim() : "";
    }

    private void validateDateRange(LocalDate from, LocalDate to) {
        if (from != null && to != null && to.isBefore(from)) {
            throw new InvalidRequestException("La date de fin doit être égale ou postérieure à la date de début.");
        }
    }

    private Instant startOfDay(LocalDate date) {
        try {
            return date == null ? null : date.atStartOfDay(appointmentZone).toInstant();
        } catch (DateTimeException exception) {
            throw new InvalidRequestException("La date fournie n'est pas prise en charge.");
        }
    }

    private Instant startOfNextDay(LocalDate date) {
        try {
            return date == null ? null : nextDay(date).atStartOfDay(appointmentZone).toInstant();
        } catch (DateTimeException exception) {
            throw new InvalidRequestException("La date fournie n'est pas prise en charge.");
        }
    }

    private LocalDate nextDay(LocalDate date) {
        try {
            return date.plusDays(1);
        } catch (DateTimeException exception) {
            throw new InvalidRequestException("La date de fin est hors de la plage prise en charge.");
        }
    }
}
