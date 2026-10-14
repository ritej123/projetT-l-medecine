package com.telemed.platform.doctor.repository;

import java.util.Optional;
import java.util.List;
import java.util.UUID;

import com.telemed.platform.doctor.domain.Doctor;
import com.telemed.platform.doctor.domain.DoctorVerificationStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface DoctorRepository extends JpaRepository<Doctor, UUID> {

    Optional<Doctor> findByUserEmail(String email);

    Optional<Doctor> findByIdAndUserEnabledTrueAndVerificationStatus(UUID id, DoctorVerificationStatus status);

    List<Doctor> findAllByUserEnabledTrueAndVerificationStatusOrderBySpecialtyAsc(
            DoctorVerificationStatus status);

    List<Doctor> findAllByVerificationStatusOrderByUserCreatedAtAsc(DoctorVerificationStatus status);

    long countByVerificationStatus(DoctorVerificationStatus status);

    long countByVerificationStatusAndUserEnabled(DoctorVerificationStatus status, boolean enabled);

    long countByUserEnabledFalse();

    @Query("""
            select new com.telemed.platform.user.api.AdminDoctorResponse(
                d.id, u.email, d.specialty, d.bio, d.verificationStatus, u.enabled, u.createdAt)
            from Doctor d join d.user u
            where (
                lower(u.email) like lower(concat('%', :search, '%')) or
                lower(d.specialty) like lower(concat('%', :search, '%')))
              and (:filterStatus = false or d.verificationStatus = :status)
              and (:filterEnabled = false or u.enabled = :enabled)
              and (:filterFromDate = false or u.createdAt >= :fromDate)
              and (:filterToDate = false or u.createdAt < :toDate)
            """)
    Page<com.telemed.platform.user.api.AdminDoctorResponse> searchAdminDoctors(
            @Param("search") String search,
            @Param("filterStatus") boolean filterStatus,
            @Param("status") DoctorVerificationStatus status,
            @Param("filterEnabled") boolean filterEnabled,
            @Param("enabled") boolean enabled,
            @Param("filterFromDate") boolean filterFromDate,
            @Param("fromDate") java.time.Instant fromDate,
            @Param("filterToDate") boolean filterToDate,
            @Param("toDate") java.time.Instant toDate,
            Pageable pageable);

    @Query("""
            select new com.telemed.platform.user.api.AdminDoctorResponse(
                d.id, u.email, d.specialty, d.bio, d.verificationStatus, u.enabled, u.createdAt)
            from Doctor d join d.user u
            where d.verificationStatus = :status
            order by u.createdAt desc
            """)
    List<com.telemed.platform.user.api.AdminDoctorResponse> findRecentByVerificationStatus(
            @Param("status") DoctorVerificationStatus status, Pageable pageable);
}
