package com.telemed.platform.availability.repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.telemed.platform.availability.domain.AvailabilityException;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AvailabilityExceptionRepository extends JpaRepository<AvailabilityException, UUID> {
    List<AvailabilityException> findAllByDoctor_IdAndExceptionDateBetweenOrderByExceptionDateAscStartTimeAsc(
            UUID doctorId, LocalDate from, LocalDate to);
    List<AvailabilityException> findAllByDoctor_IdAndExceptionDateOrderByStartTimeAsc(UUID doctorId, LocalDate date);
    Optional<AvailabilityException> findByIdAndDoctor_Id(UUID id, UUID doctorId);
}
