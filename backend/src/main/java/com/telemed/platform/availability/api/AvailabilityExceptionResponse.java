package com.telemed.platform.availability.api;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.UUID;

public record AvailabilityExceptionResponse(
        UUID id, LocalDate date, LocalTime startTime, LocalTime endTime, String reason) {
}
