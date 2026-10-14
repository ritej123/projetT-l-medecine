package com.telemed.platform.doctor.mapper;

import com.telemed.platform.doctor.api.DoctorResponse;
import com.telemed.platform.doctor.domain.Doctor;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface DoctorMapper {

    DoctorResponse toResponse(Doctor doctor);
}
