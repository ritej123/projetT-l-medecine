package com.telemed.platform.appointment.service;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;

import com.telemed.platform.appointment.api.DoctorDashboardAppointmentResponse;
import com.telemed.platform.appointment.api.DoctorDashboardStatsResponse;
import com.telemed.platform.appointment.domain.Appointment;
import com.telemed.platform.appointment.domain.AppointmentStatus;
import com.telemed.platform.appointment.repository.AppointmentRepository;
import com.telemed.platform.common.exception.NotFoundException;
import com.telemed.platform.doctor.repository.DoctorRepository;
import org.springframework.stereotype.Service;
import org.springframework.data.domain.PageRequest;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DoctorDashboardService {

    private static final List<AppointmentStatus> ACTIVE_STATUSES =
            List.of(AppointmentStatus.PENDING, AppointmentStatus.CONFIRMED);
    private static final List<AppointmentStatus> CARE_STATUSES =
            List.of(AppointmentStatus.CONFIRMED, AppointmentStatus.DONE);

    private final DoctorRepository doctorRepository;
    private final AppointmentRepository appointmentRepository;
    private final Clock clock;
    private final ZoneId appointmentZone;

    public DoctorDashboardService(
            DoctorRepository doctorRepository,
            AppointmentRepository appointmentRepository,
            Clock clock,
            ZoneId appointmentZone) {
        this.doctorRepository = doctorRepository;
        this.appointmentRepository = appointmentRepository;
        this.clock = clock;
        this.appointmentZone = appointmentZone;
    }

    @Transactional(readOnly = true)
    public DoctorDashboardStatsResponse findMine(String email) {
        UUID doctorId = doctorRepository.findByUserEmail(email)
                .orElseThrow(() -> new NotFoundException("Profil médecin introuvable."))
                .getId();
        LocalDate today = LocalDate.now(clock.withZone(appointmentZone));
        LocalDateTime start = today.atStartOfDay();
        LocalDateTime end = today.plusDays(1).atStartOfDay();
        LocalDateTime now = LocalDateTime.now(clock.withZone(appointmentZone));
        List<Appointment> next = appointmentRepository
                .findAllByDoctorIdAndScheduledAtGreaterThanEqualAndScheduledAtLessThanAndStatusInOrderByScheduledAtAsc(
                        doctorId, now, today.plusDays(31).atStartOfDay(), ACTIVE_STATUSES);
        List<DoctorDashboardAppointmentResponse> recent = appointmentRepository
                .findAllByDoctorIdAndScheduledAtLessThanOrderByScheduledAtDesc(
                        doctorId, now, PageRequest.of(0, 8)).stream()
                .map(DoctorDashboardService::toResponse)
                .toList();
        return new DoctorDashboardStatsResponse(
                appointmentRepository.countByDoctorIdAndScheduledAtGreaterThanEqualAndScheduledAtLessThan(
                        doctorId, start, end),
                appointmentRepository.countByDoctorIdAndScheduledAtGreaterThanEqualAndStatusIn(
                        doctorId, now, ACTIVE_STATUSES),
                appointmentRepository.countByDoctorIdAndStatusAndScheduledAtGreaterThanEqualAndScheduledAtLessThan(
                        doctorId, AppointmentStatus.DONE, start, end),
                appointmentRepository.countByDoctorIdAndStatusAndScheduledAtGreaterThanEqualAndScheduledAtLessThan(
                        doctorId, AppointmentStatus.PENDING, start, end),
                appointmentRepository.countByDoctorIdAndStatusAndScheduledAtGreaterThanEqualAndScheduledAtLessThan(
                        doctorId, AppointmentStatus.CANCELLED, start, end),
                appointmentRepository.countDistinctByDoctorIdAndStatusIn(doctorId, CARE_STATUSES),
                next.stream().limit(8).map(DoctorDashboardService::toResponse).toList(),
                recent);
    }

    private static DoctorDashboardAppointmentResponse toResponse(Appointment appointment) {
        return new DoctorDashboardAppointmentResponse(
                appointment.getId(),
                appointment.getPatient().getId(),
                appointment.getPatient().getUser().getEmail(),
                appointment.getScheduledAt(),
                appointment.getDurationMinutes(),
                appointment.getStatus());
    }
}
