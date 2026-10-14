package com.telemed.platform.appointment.api;

import java.util.List;

public record DoctorDashboardStatsResponse(
        long scheduledToday,
        long upcomingAppointments,
        long completedToday,
        long pendingToday,
        long cancelledToday,
        long followedPatients,
        List<DoctorDashboardAppointmentResponse> nextAppointments,
        List<DoctorDashboardAppointmentResponse> recentActivities) {
}
