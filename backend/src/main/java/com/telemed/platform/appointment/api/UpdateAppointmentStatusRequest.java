package com.telemed.platform.appointment.api;

import com.telemed.platform.appointment.domain.AppointmentStatus;
import jakarta.validation.constraints.NotNull;

public record UpdateAppointmentStatusRequest(@NotNull AppointmentStatus status) {
}
