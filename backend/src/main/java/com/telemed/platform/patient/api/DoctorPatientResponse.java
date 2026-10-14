package com.telemed.platform.patient.api;

import java.time.LocalDateTime;
import java.util.UUID;

public record DoctorPatientResponse(
        UUID patientId, String email, LocalDateTime lastAppointment, LocalDateTime nextAppointment) {
}
