package com.telemed.platform.appointment.api;

import java.time.LocalDateTime;
import java.util.UUID;

public record AvailableAppointmentSlotResponse(
        UUID doctorId,
        LocalDateTime startsAt,
        LocalDateTime endsAt) {
}
