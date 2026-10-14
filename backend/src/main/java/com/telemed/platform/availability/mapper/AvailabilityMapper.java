package com.telemed.platform.availability.mapper;

import com.telemed.platform.availability.api.AvailabilityResponse;
import com.telemed.platform.availability.domain.Availability;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface AvailabilityMapper {

    @Mapping(target = "doctorId", source = "doctor.id")
    AvailabilityResponse toResponse(Availability availability);
}
