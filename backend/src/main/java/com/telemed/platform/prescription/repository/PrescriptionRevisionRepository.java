package com.telemed.platform.prescription.repository;

import java.util.List;
import java.util.UUID;

import com.telemed.platform.prescription.domain.PrescriptionRevision;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PrescriptionRevisionRepository extends JpaRepository<PrescriptionRevision, UUID> {

    List<PrescriptionRevision> findAllByPrescriptionIdOrderByRevisionNumberAsc(UUID prescriptionId);

    int countByPrescriptionId(UUID prescriptionId);
}
