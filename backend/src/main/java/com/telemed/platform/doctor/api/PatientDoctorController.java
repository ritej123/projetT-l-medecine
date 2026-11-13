package com.telemed.platform.doctor.api;

import java.util.List;
import java.util.UUID;

import com.telemed.platform.availability.api.AvailabilityResponse;
import com.telemed.platform.availability.repository.AvailabilityRepository;
import com.telemed.platform.doctor.domain.Doctor;
import com.telemed.platform.doctor.domain.DoctorVerificationStatus;
import com.telemed.platform.doctor.repository.DoctorRepository;
import com.telemed.platform.common.exception.NotFoundException;
import org.springframework.data.domain.Sort;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/patient/doctors")
@PreAuthorize("hasRole('PATIENT')")
public class PatientDoctorController {

    private final DoctorRepository doctorRepository;
    private final AvailabilityRepository availabilityRepository;

    public PatientDoctorController(
            DoctorRepository doctorRepository,
            AvailabilityRepository availabilityRepository) {
        this.doctorRepository = doctorRepository;
        this.availabilityRepository = availabilityRepository;
    }

    @GetMapping
    public List<PatientDoctorResponse> findAvailable(
            @RequestParam(required = false) String search,
            @RequestParam(required = false) String specialty,
            @RequestParam(required = false) String city) {
        List<Doctor> doctors = doctorRepository
                .findAllByUserEnabledTrueAndVerificationStatusOrderBySpecialtyAsc(DoctorVerificationStatus.VERIFIED);
        return doctors.stream()
                .filter(doctor -> matches(doctor, search, specialty, city))
                .map(this::toResponse)
                .toList();
    }

    @GetMapping("/{doctorId}")
    public PatientDoctorDetailResponse findById(@PathVariable UUID doctorId) {
        Doctor doctor = doctorRepository
                .findByIdAndUserEnabledTrueAndVerificationStatus(doctorId, DoctorVerificationStatus.VERIFIED)
                .orElseThrow(() -> new NotFoundException("Médecin introuvable."));
        List<AvailabilityResponse> availabilities = availabilityRepository
                .findAllByDoctorIdOrderByDayOfWeekAscStartTimeAsc(doctorId)
                .stream()
                .map(availability -> new AvailabilityResponse(
                        availability.getId(),
                        doctorId,
                        availability.getDayOfWeek(),
                        availability.getStartTime(),
                        availability.getEndTime(),
                        availability.getSlotDurationMinutes()))
                .toList();
        return new PatientDoctorDetailResponse(
                doctor.getId(),
                doctor.getFirstName(),
                doctor.getLastName(),
                doctor.getSpecialty(),
                doctor.getOfficeAddress(),
                doctor.getOfficeCity(),
                doctor.getProfilePhotoUrl(),
                doctor.getBio(),
                availabilities);
    }

    private boolean matches(Doctor doctor, String search, String specialty, String city) {
        if (search != null && !search.isBlank()) {
            String term = search.trim().toLowerCase();
            String fullName = ((doctor.getFirstName() != null ? doctor.getFirstName() : "") + " " +
                    (doctor.getLastName() != null ? doctor.getLastName() : "")).toLowerCase();
            if (!fullName.contains(term) && !(doctor.getSpecialty() != null && doctor.getSpecialty().toLowerCase().contains(term))) {
                return false;
            }
        }
        if (specialty != null && !specialty.isBlank()) {
            if (doctor.getSpecialty() == null || !doctor.getSpecialty().equalsIgnoreCase(specialty.trim())) {
                return false;
            }
        }
        if (city != null && !city.isBlank()) {
            if (doctor.getOfficeCity() == null || !doctor.getOfficeCity().toLowerCase().contains(city.trim().toLowerCase())) {
                return false;
            }
        }
        return true;
    }

    private PatientDoctorResponse toResponse(Doctor doctor) {
        return new PatientDoctorResponse(
                doctor.getId(),
                doctor.getFirstName(),
                doctor.getLastName(),
                doctor.getSpecialty(),
                doctor.getOfficeAddress(),
                doctor.getOfficeCity(),
                doctor.getProfessionalNumber(),
                doctor.getProfilePhotoUrl());
    }
}