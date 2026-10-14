package com.telemed.platform.appointment.repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import com.telemed.platform.appointment.domain.Appointment;
import com.telemed.platform.appointment.domain.AppointmentStatus;
import com.telemed.platform.appointment.api.AdminAppointmentResponse;
import com.telemed.platform.patient.api.DoctorPatientResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AppointmentRepository extends JpaRepository<Appointment, UUID> {

    @EntityGraph(attributePaths = {"doctor.user"})
    java.util.Optional<Appointment> findWithDoctorUserById(UUID id);

    List<Appointment> findAllByPatientIdOrderByScheduledAtDesc(UUID patientId);

    List<Appointment> findAllByDoctorIdOrderByScheduledAtAsc(UUID doctorId);

    List<Appointment> findAllByDoctorIdAndScheduledAtLessThanOrderByScheduledAtDesc(
            UUID doctorId, LocalDateTime before, Pageable pageable);

    long countByDoctorIdAndScheduledAtGreaterThanEqualAndScheduledAtLessThan(
            UUID doctorId, LocalDateTime from, LocalDateTime to);

    long countByDoctorIdAndStatusAndScheduledAtGreaterThanEqualAndScheduledAtLessThan(
            UUID doctorId, AppointmentStatus status, LocalDateTime from, LocalDateTime to);

    long countByDoctorIdAndScheduledAtGreaterThanEqualAndStatusIn(
            UUID doctorId, LocalDateTime from, List<AppointmentStatus> statuses);

    long countDistinctByDoctorIdAndStatusIn(UUID doctorId, List<AppointmentStatus> statuses);

    boolean existsByDoctorIdAndPatientIdAndStatusIn(
            UUID doctorId, UUID patientId, List<AppointmentStatus> statuses);

    List<Appointment> findAllByDoctorIdAndPatientIdAndStatusInOrderByScheduledAtDesc(
            UUID doctorId, UUID patientId, List<AppointmentStatus> statuses);

    @Query(value = """
            select new com.telemed.platform.patient.api.DoctorPatientResponse(
                p.id, u.email,
                max(case when a.status = com.telemed.platform.appointment.domain.AppointmentStatus.DONE
                               and a.scheduledAt <= :now then a.scheduledAt else null end),
                min(case when a.scheduledAt >= :now then a.scheduledAt else null end))
            from Appointment a
            join a.patient p
            join p.user u
            where a.doctor.id = :doctorId
              and a.status in :statuses
              and lower(u.email) like :search
            group by p.id, u.email
            order by lower(u.email) asc
            """,
            countQuery = """
            select count(distinct p.id)
            from Appointment a
            join a.patient p
            join p.user u
            where a.doctor.id = :doctorId
              and a.status in :statuses
              and lower(u.email) like :search
            """)
    Page<DoctorPatientResponse> findDoctorPatients(
            @Param("doctorId") UUID doctorId,
            @Param("statuses") List<AppointmentStatus> statuses,
            @Param("now") LocalDateTime now,
            @Param("search") String search,
            Pageable pageable);

    List<Appointment> findAllByDoctorIdAndScheduledAtGreaterThanEqualAndScheduledAtLessThanAndStatusInOrderByScheduledAtAsc(
            UUID doctorId,
            LocalDateTime from,
            LocalDateTime to,
            List<AppointmentStatus> statuses);

    List<Appointment> findAllByScheduledAtGreaterThanEqualAndScheduledAtLessThanAndStatusInOrderByScheduledAtAsc(
            LocalDateTime from, LocalDateTime to, List<AppointmentStatus> statuses);


    boolean existsByDoctorIdAndScheduledAtAndStatusIn(
            UUID doctorId, LocalDateTime scheduledAt, List<AppointmentStatus> statuses);

    long countByStatus(AppointmentStatus status);

    long countByScheduledAtGreaterThanEqualAndScheduledAtLessThan(
            LocalDateTime from, LocalDateTime to);

    long countByScheduledAtGreaterThanEqualAndStatusIn(
            LocalDateTime from, List<AppointmentStatus> statuses);

    long countByScheduledAtGreaterThanEqualAndScheduledAtLessThanAndStatusIn(
            LocalDateTime from, LocalDateTime to, List<AppointmentStatus> statuses);

    @Query("""
            select new com.telemed.platform.appointment.api.AdminAppointmentResponse(
                a.id, patientUser.email, doctorUser.email, d.specialty,
                a.scheduledAt, a.durationMinutes, a.status)
            from Appointment a
            join a.patient p
            join p.user patientUser
            join a.doctor d
            join d.user doctorUser
            where (
                lower(patientUser.email) like lower(concat('%', :search, '%')) or
                lower(doctorUser.email) like lower(concat('%', :search, '%')))
              and (:filterStatus = false or a.status = :status)
              and (:filterFromDate = false or a.scheduledAt >= :fromDate)
              and (:filterToDate = false or a.scheduledAt < :toDate)
            """)
    Page<AdminAppointmentResponse> searchAdminAppointments(
            @Param("search") String search,
            @Param("filterStatus") boolean filterStatus,
            @Param("status") AppointmentStatus status,
            @Param("filterFromDate") boolean filterFromDate,
            @Param("fromDate") LocalDateTime fromDate,
            @Param("filterToDate") boolean filterToDate,
            @Param("toDate") LocalDateTime toDate,
            Pageable pageable);

    @Query("""
            select new com.telemed.platform.appointment.api.AdminAppointmentResponse(
                a.id, patientUser.email, doctorUser.email, d.specialty,
                a.scheduledAt, a.durationMinutes, a.status)
            from Appointment a
            join a.patient p
            join p.user patientUser
            join a.doctor d
            join d.user doctorUser
            order by a.scheduledAt desc
            """)
    java.util.List<AdminAppointmentResponse> findRecentAdminAppointments(Pageable pageable);
}
