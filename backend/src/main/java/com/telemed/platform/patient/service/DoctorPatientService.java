package com.telemed.platform.patient.service;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;

import com.telemed.platform.appointment.domain.Appointment;
import com.telemed.platform.appointment.domain.AppointmentStatus;
import com.telemed.platform.appointment.repository.AppointmentRepository;
import com.telemed.platform.common.exception.InvalidRequestException;
import com.telemed.platform.common.exception.NotFoundException;
import com.telemed.platform.consultation.repository.ConsultationRepository;
import com.telemed.platform.doctor.repository.DoctorRepository;
import com.telemed.platform.patient.api.DoctorPatientDetailResponse;
import com.telemed.platform.patient.api.DoctorPatientResponse;
import com.telemed.platform.patient.domain.Patient;
import com.telemed.platform.patient.repository.PatientRepository;
import com.telemed.platform.user.api.AdminPageResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DoctorPatientService {

    private static final List<AppointmentStatus> CARE_STATUSES =
            List.of(AppointmentStatus.CONFIRMED, AppointmentStatus.DONE);

    private final DoctorRepository doctorRepository;
    private final PatientRepository patientRepository;
    private final AppointmentRepository appointmentRepository;
    private final ConsultationRepository consultationRepository;
    private final Clock clock;
    private final ZoneId appointmentZone;

    public DoctorPatientService(
            DoctorRepository doctorRepository,
            PatientRepository patientRepository,
            AppointmentRepository appointmentRepository,
            ConsultationRepository consultationRepository,
            Clock clock,
            ZoneId appointmentZone) {
        this.doctorRepository = doctorRepository;
        this.patientRepository = patientRepository;
        this.appointmentRepository = appointmentRepository;
        this.consultationRepository = consultationRepository;
        this.clock = clock;
        this.appointmentZone = appointmentZone;
    }

    @Transactional(readOnly = true)
    public AdminPageResponse<DoctorPatientResponse> findMine(String email, String search, Pageable pageable) {
        if (pageable.getPageNumber() < 0 || pageable.getPageSize() < 1 || pageable.getPageSize() > 100) {
            throw new InvalidRequestException("La pagination demandée est invalide.");
        }
        UUID doctorId = doctorRepository.findByUserEmail(email)
                .orElseThrow(() -> new NotFoundException("Profil médecin introuvable."))
                .getId();
        Pageable safePage = PageRequest.of(pageable.getPageNumber(), pageable.getPageSize());
        LocalDateTime now = LocalDateTime.now(clock.withZone(appointmentZone));
        Page<DoctorPatientResponse> result = appointmentRepository.findDoctorPatients(
                doctorId, CARE_STATUSES, now, "%" + (search == null ? "" : search.trim().toLowerCase()) + "%", safePage);
        return AdminPageResponse.from(result);
    }

    @Transactional(readOnly = true)
    public DoctorPatientDetailResponse findPatient(String email, UUID patientId) {
        UUID doctorId = doctorRepository.findByUserEmail(email)
                .orElseThrow(() -> new NotFoundException("Profil médecin introuvable."))
                .getId();
        Patient patient = patientRepository.findById(patientId)
                .filter(candidate -> appointmentRepository
                        .existsByDoctorIdAndPatientIdAndStatusIn(doctorId, patientId, CARE_STATUSES))
                .orElseThrow(() -> new NotFoundException("Patient introuvable ou accès non autorisé."));
        List<Appointment> appointments = appointmentRepository
                .findAllByDoctorIdAndPatientIdAndStatusInOrderByScheduledAtDesc(doctorId, patientId, CARE_STATUSES)
                ;
        List<DoctorPatientDetailResponse.AppointmentHistory> history = appointments.stream()
                .map(appointment -> new DoctorPatientDetailResponse.AppointmentHistory(
                        appointment.getId(), appointment.getScheduledAt(), appointment.getDurationMinutes(),
                        appointment.getStatus()))
                .toList();
        List<DoctorPatientDetailResponse.ConsultationHistory> clinicalHistory = consultationRepository
                .findAllByAppointmentIdIn(appointments.stream().map(Appointment::getId).toList()).stream()
                .map(consultation -> new DoctorPatientDetailResponse.ConsultationHistory(
                        consultation.getAppointment().getId(),
                        consultation.getAppointment().getScheduledAt(),
                        consultation.getReport(),
                        consultation.getObservations(),
                        consultation.getDiagnosis()))
                .toList();
        return new DoctorPatientDetailResponse(
                patient.getId(), patient.getUser().getEmail(), patient.getMedicalRecord(), history, clinicalHistory);
    }
}
