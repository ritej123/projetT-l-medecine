package com.telemed.platform.patient.repository;

import java.util.Optional;
import java.util.UUID;

import com.telemed.platform.patient.domain.Patient;
import com.telemed.platform.user.api.AdminPatientResponse;
import java.time.Instant;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PatientRepository extends JpaRepository<Patient, UUID> {

    Optional<Patient> findByUserEmail(String email);

    long countByUserEnabled(boolean enabled);

    @Query("""
            select new com.telemed.platform.user.api.AdminPatientResponse(
                p.id, u.email, u.enabled, u.createdAt)
            from Patient p join p.user u
            where lower(u.email) like lower(concat('%', :search, '%'))
              and (:filterEnabled = false or u.enabled = :enabled)
              and (:filterFromDate = false or u.createdAt >= :fromDate)
              and (:filterToDate = false or u.createdAt < :toDate)
            """)
    Page<AdminPatientResponse> searchAdminPatients(
            @Param("search") String search,
            @Param("filterEnabled") boolean filterEnabled,
            @Param("enabled") boolean enabled,
            @Param("filterFromDate") boolean filterFromDate,
            @Param("fromDate") Instant fromDate,
            @Param("filterToDate") boolean filterToDate,
            @Param("toDate") Instant toDate,
            Pageable pageable);
}
