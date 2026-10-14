package com.telemed.platform.availability.api;

import java.time.LocalDate;
import java.time.LocalTime;

import jakarta.validation.constraints.Size;

public record AvailabilityExceptionRequest(
        LocalDate date, LocalTime startTime, LocalTime endTime, @Size(max = 240) String reason) {
}
