package com.telemed.platform.user.service;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;

import com.telemed.platform.appointment.api.AdminAppointmentResponse;
import com.telemed.platform.appointment.domain.AppointmentStatus;
import com.telemed.platform.appointment.repository.AppointmentRepository;
import com.telemed.platform.doctor.domain.DoctorVerificationStatus;
import com.telemed.platform.doctor.repository.DoctorRepository;
import com.telemed.platform.patient.repository.PatientRepository;
import com.telemed.platform.user.api.AdminDashboardStats;
import com.telemed.platform.user.api.AdminDoctorResponse;
import com.telemed.platform.user.api.AdminMonthlyMetric;
import com.telemed.platform.user.domain.Role;
import com.telemed.platform.user.repository.UserRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AdminDashboardService {

    private static final List<AppointmentStatus> ACTIVE_APPOINTMENT_STATUSES =
            List.of(AppointmentStatus.PENDING, AppointmentStatus.CONFIRMED);

    private final UserRepository userRepository;
    private final PatientRepository patientRepository;
    private final DoctorRepository doctorRepository;
    private final AppointmentRepository appointmentRepository;
    private final Clock clock;
    private final ZoneId appointmentZone;

    public AdminDashboardService(
            UserRepository userRepository,
            PatientRepository patientRepository,
            DoctorRepository doctorRepository,
            AppointmentRepository appointmentRepository,
            Clock clock,
            ZoneId appointmentZone) {
        this.userRepository = userRepository;
        this.patientRepository = patientRepository;
        this.doctorRepository = doctorRepository;
        this.appointmentRepository = appointmentRepository;
        this.clock = clock;
        this.appointmentZone = appointmentZone;
    }

    @Transactional(readOnly = true)
    public AdminDashboardStats getStats() {
        LocalDate today = LocalDate.now(clock.withZone(appointmentZone));
        LocalDateTime todayStart = today.atStartOfDay();
        LocalDateTime tomorrowStart = today.plusDays(1).atStartOfDay();
        LocalDateTime now = LocalDateTime.now(clock.withZone(appointmentZone));

        List<AdminMonthlyMetric> monthlyActivity = new ArrayList<>();
        YearMonth currentMonth = YearMonth.from(today);
        for (int offset = 5; offset >= 0; offset--) {
            YearMonth month = currentMonth.minusMonths(offset);
            LocalDateTime monthStart = month.atDay(1).atStartOfDay();
            LocalDateTime nextMonthStart = month.plusMonths(1).atDay(1).atStartOfDay();
            Instant registrationStart = monthStart.atZone(appointmentZone).toInstant();
            Instant registrationEnd = nextMonthStart.atZone(appointmentZone).toInstant();
            monthlyActivity.add(new AdminMonthlyMetric(
                    month.toString(),
                    userRepository.countByCreatedAtGreaterThanEqualAndCreatedAtLessThan(
                            registrationStart, registrationEnd),
                    appointmentRepository.countByScheduledAtGreaterThanEqualAndScheduledAtLessThan(
                            monthStart, nextMonthStart)));
        }

        return new AdminDashboardStats(
                patientRepository.count(),
                doctorRepository.count(),
                doctorRepository.countByVerificationStatus(DoctorVerificationStatus.PENDING),
                doctorRepository.countByVerificationStatusAndUserEnabled(
                        DoctorVerificationStatus.VERIFIED, true),
                patientRepository.countByUserEnabled(false)
                        + doctorRepository.countByVerificationStatusAndUserEnabled(
                                DoctorVerificationStatus.VERIFIED, false),
                appointmentRepository.count(),
                appointmentRepository.countByScheduledAtGreaterThanEqualAndScheduledAtLessThan(
                        todayStart, tomorrowStart),
                appointmentRepository.countByScheduledAtGreaterThanEqualAndStatusIn(
                        now, ACTIVE_APPOINTMENT_STATUSES),
                appointmentRepository.countByStatus(AppointmentStatus.CANCELLED),
                List.copyOf(monthlyActivity),
                doctorRepository.findRecentByVerificationStatus(
                        DoctorVerificationStatus.PENDING, PageRequest.of(0, 5)),
                appointmentRepository.findRecentAdminAppointments(PageRequest.of(0, 8)));
    }
}
