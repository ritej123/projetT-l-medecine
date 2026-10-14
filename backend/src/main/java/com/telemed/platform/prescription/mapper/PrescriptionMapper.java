package com.telemed.platform.prescription.mapper;

import com.telemed.platform.prescription.api.PrescriptionResponse;
import com.telemed.platform.prescription.domain.Prescription;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface PrescriptionMapper {

    @Mapping(target = "appointmentId", source = "appointment.id")
    PrescriptionResponse toResponse(Prescription prescription);

    @Mapping(target = "id", source = "id")
    @Mapping(target = "revisionNumber", source = "revisionNumber")
    @Mapping(target = "content", source = "content")
    @Mapping(target = "actorUserId", source = "actorUserId")
    @Mapping(target = "changeType", source = "changeType")
    @Mapping(target = "createdAt", source = "createdAt")
    com.telemed.platform.prescription.api.PrescriptionRevisionResponse toRevisionResponse(
            com.telemed.platform.prescription.domain.PrescriptionRevision revision);
}
