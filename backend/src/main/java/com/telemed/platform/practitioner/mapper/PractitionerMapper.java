package com.telemed.platform.practitioner.mapper;

import com.telemed.platform.practitioner.api.PractitionerResponse;
import com.telemed.platform.practitioner.domain.Practitioner;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface PractitionerMapper {

    PractitionerResponse toResponse(Practitioner practitioner);
}
