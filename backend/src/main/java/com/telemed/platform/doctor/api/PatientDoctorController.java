package com.telemed.platform.doctor.api;

import java.util.List;

import com.telemed.platform.doctor.service.DoctorService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/patient/doctors")
@PreAuthorize("hasRole('PATIENT')")
public class PatientDoctorController {

    private final DoctorService doctorService;

    public PatientDoctorController(DoctorService doctorService) {
        this.doctorService = doctorService;
    }

    @GetMapping
    public List<DoctorResponse> findAll() {
        return doctorService.findAll();
    }
}
