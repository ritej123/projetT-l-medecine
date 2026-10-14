package com.telemed.platform.appointment.mapper;

import com.telemed.platform.appointment.api.AppointmentResponse;
import com.telemed.platform.appointment.domain.Appointment;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface AppointmentMapper {

    @Mapping(target = "patientId", source = "patient.id")
    @Mapping(target = "doctorId", source = "doctor.id")
    AppointmentResponse toResponse(Appointment appointment);
}
