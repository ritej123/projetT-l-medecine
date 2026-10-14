package com.telemed.platform.availability.repository;

import java.time.DayOfWeek;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;

import com.telemed.platform.availability.domain.Availability;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AvailabilityRepository extends JpaRepository<Availability, UUID> {

    List<Availability> findAllByDoctorIdOrderByDayOfWeekAscStartTimeAsc(UUID doctorId);

    List<Availability> findAllByDoctorIdAndDayOfWeekOrderByStartTimeAsc(UUID doctorId, DayOfWeek dayOfWeek);

    boolean existsByDoctorIdAndDayOfWeekAndStartTimeLessThanAndEndTimeGreaterThan(
            UUID doctorId, DayOfWeek dayOfWeek, LocalTime endTime, LocalTime startTime);

    boolean existsByDoctorIdAndDayOfWeekAndStartTimeLessThanEqualAndEndTimeGreaterThan(
            UUID doctorId, DayOfWeek dayOfWeek, LocalTime startTime, LocalTime endTime);
}
