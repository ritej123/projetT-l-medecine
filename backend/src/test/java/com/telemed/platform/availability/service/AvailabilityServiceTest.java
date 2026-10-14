package com.telemed.platform.availability.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.DayOfWeek;
import java.time.Clock;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.Optional;
import java.util.UUID;

import com.telemed.platform.availability.api.AvailabilityResponse;
import com.telemed.platform.availability.api.CreateAvailabilityRequest;
import com.telemed.platform.availability.domain.Availability;
import com.telemed.platform.availability.mapper.AvailabilityMapper;
import com.telemed.platform.availability.repository.AvailabilityRepository;
import com.telemed.platform.appointment.repository.AppointmentRepository;
import com.telemed.platform.common.exception.ConflictException;
import com.telemed.platform.doctor.domain.Doctor;
import com.telemed.platform.doctor.repository.DoctorRepository;
import com.telemed.platform.user.domain.Role;
import com.telemed.platform.user.domain.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AvailabilityServiceTest {

    @Mock
    private DoctorRepository doctorRepository;

    @Mock
    private AvailabilityRepository availabilityRepository;

    @Mock
    private AvailabilityMapper availabilityMapper;

    @Mock
    private AppointmentRepository appointmentRepository;

    private AvailabilityService service;

    @BeforeEach
    void setUp() {
        service = new AvailabilityService(
                doctorRepository,
                availabilityRepository,
                availabilityMapper,
                appointmentRepository,
                Clock.systemUTC(),
                ZoneId.of("UTC"));
    }

    @Test
    void createsAvailabilityWhenThereIsNoOverlap() {
        UUID doctorId = UUID.randomUUID();
        Doctor doctor = new Doctor(new User("doctor@example.test", "hash", Role.DOCTOR), "General", null);
        CreateAvailabilityRequest request =
                new CreateAvailabilityRequest(DayOfWeek.MONDAY, LocalTime.of(9, 0), LocalTime.of(12, 0), 30);
        AvailabilityResponse expected =
                new AvailabilityResponse(
                        UUID.randomUUID(), doctorId, DayOfWeek.MONDAY, request.startTime(), request.endTime(), 30);
        when(doctorRepository.findByUserEmail("doctor@example.test")).thenReturn(Optional.of(doctor));
        when(availabilityRepository.existsByDoctorIdAndDayOfWeekAndStartTimeLessThanAndEndTimeGreaterThan(
                any(), any(), any(), any())).thenReturn(false);
        when(availabilityRepository.save(any(Availability.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(availabilityMapper.toResponse(any(Availability.class))).thenReturn(expected);

        assertThat(service.create("doctor@example.test", request)).isEqualTo(expected);
        verify(availabilityRepository).save(any(Availability.class));
    }

    @Test
    void rejectsOverlappingAvailability() {
        Doctor doctor = new Doctor(new User("doctor@example.test", "hash", Role.DOCTOR), "General", null);
        when(doctorRepository.findByUserEmail("doctor@example.test")).thenReturn(Optional.of(doctor));
        when(availabilityRepository.existsByDoctorIdAndDayOfWeekAndStartTimeLessThanAndEndTimeGreaterThan(
                any(), any(), any(), any())).thenReturn(true);

        assertThatThrownBy(() -> service.create("doctor@example.test",
                new CreateAvailabilityRequest(DayOfWeek.MONDAY, LocalTime.of(10, 0), LocalTime.of(11, 0), 30)))
                .isInstanceOf(ConflictException.class);
    }
}
