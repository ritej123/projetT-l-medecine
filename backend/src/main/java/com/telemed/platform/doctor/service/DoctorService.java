package com.telemed.platform.doctor.service;

import java.util.List;

import com.telemed.platform.doctor.api.DoctorResponse;
import com.telemed.platform.doctor.domain.DoctorVerificationStatus;
import com.telemed.platform.doctor.mapper.DoctorMapper;
import com.telemed.platform.doctor.repository.DoctorRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DoctorService {

    private final DoctorRepository doctorRepository;
    private final DoctorMapper doctorMapper;

    public DoctorService(DoctorRepository doctorRepository, DoctorMapper doctorMapper) {
        this.doctorRepository = doctorRepository;
        this.doctorMapper = doctorMapper;
    }

    @Transactional(readOnly = true)
    public List<DoctorResponse> findAll() {
        return doctorRepository.findAllByUserEnabledTrueAndVerificationStatusOrderBySpecialtyAsc(
                        DoctorVerificationStatus.VERIFIED)
                .stream()
                .map(doctorMapper::toResponse)
                .toList();
    }
}
