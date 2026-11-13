package com.telemed.platform.prescription.repository;

import java.util.List;
import java.util.UUID;

import com.telemed.platform.prescription.domain.PrescriptionRevision;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PrescriptionRevisionRepository extends JpaRepository<PrescriptionRevision, UUID> {

    List<PrescriptionRevision> findAllByPrescriptionIdOrderByRevisionNumberAsc(UUID prescriptionId);

    @Query("select coalesce(max(revision.revisionNumber), 0) from PrescriptionRevision revision "
            + "where revision.prescription.id = :prescriptionId")
    int findMaxRevisionNumber(@Param("prescriptionId") UUID prescriptionId);
}
