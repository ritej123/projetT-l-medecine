package com.telemed.platform.patient.mapper;

import com.telemed.platform.patient.api.PatientResponse;
import com.telemed.platform.patient.domain.Patient;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface PatientMapper {

    @Mapping(target = "userId", source = "user.id")
    PatientResponse toResponse(Patient patient);
}
