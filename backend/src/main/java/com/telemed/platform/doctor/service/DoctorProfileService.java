package com.telemed.platform.doctor.service;

import com.telemed.platform.common.exception.NotFoundException;
import com.telemed.platform.doctor.api.DoctorProfileRequest;
import com.telemed.platform.doctor.api.DoctorProfileResponse;
import com.telemed.platform.doctor.domain.Doctor;
import com.telemed.platform.doctor.repository.DoctorRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DoctorProfileService {

    private final DoctorRepository doctorRepository;

    public DoctorProfileService(DoctorRepository doctorRepository) {
        this.doctorRepository = doctorRepository;
    }

    @Transactional(readOnly = true)
    public DoctorProfileResponse findMine(String email) {
        return toResponse(findDoctor(email));
    }

    @Transactional
    public DoctorProfileResponse updateMine(String email, DoctorProfileRequest request) {
        Doctor doctor = findDoctor(email);
        doctor.updateProfile(
                clean(request.firstName()),
                clean(request.lastName()),
                clean(request.profilePhotoUrl()),
                clean(request.professionalPhone()),
                clean(request.officeAddress()),
                clean(request.professionalNumber()),
                request.emailNotificationsEnabled());
        return toResponse(doctorRepository.save(doctor));
    }

    private Doctor findDoctor(String email) {
        return doctorRepository.findByUserEmail(email)
                .orElseThrow(() -> new NotFoundException("Profil médecin introuvable."));
    }

    private String clean(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private DoctorProfileResponse toResponse(Doctor doctor) {
        return new DoctorProfileResponse(
                doctor.getId(), doctor.getUser().getEmail(), doctor.getFirstName(), doctor.getLastName(),
                doctor.getProfilePhotoUrl(), doctor.getSpecialty(), doctor.getBio(), doctor.getProfessionalPhone(),
                doctor.getOfficeAddress(), doctor.getProfessionalNumber(), doctor.isProfessionalNumberVerified(),
                doctor.isEmailNotificationsEnabled(), doctor.getVerificationStatus());
    }
}
