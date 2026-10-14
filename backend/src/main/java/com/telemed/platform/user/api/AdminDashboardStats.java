package com.telemed.platform.user.api;

import java.util.List;

import com.telemed.platform.appointment.api.AdminAppointmentResponse;

public record AdminDashboardStats(
        long totalPatients,
        long totalDoctors,
        long pendingDoctors,
        long verifiedActiveDoctors,
        long suspendedAccounts,
        long totalAppointments,
        long appointmentsToday,
        long upcomingAppointments,
        long cancelledAppointments,
        List<AdminMonthlyMetric> monthlyActivity,
        List<AdminDoctorResponse> recentDoctorApplications,
        List<AdminAppointmentResponse> recentAppointments) {
}
