package com.telemed.platform.doctor.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import java.util.List;

import com.telemed.platform.doctor.api.DoctorResponse;
import com.telemed.platform.doctor.domain.Doctor;
import com.telemed.platform.doctor.domain.DoctorVerificationStatus;
import com.telemed.platform.doctor.mapper.DoctorMapper;
import com.telemed.platform.doctor.repository.DoctorRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class DoctorServiceTest {

    @Mock
    private DoctorRepository doctorRepository;

    @Mock
    private DoctorMapper doctorMapper;

    @InjectMocks
    private DoctorService doctorService;

    @Test
    void returnsDoctorProfilesAsDtos() {
        Doctor first = org.mockito.Mockito.mock(Doctor.class);
        Doctor second = org.mockito.Mockito.mock(Doctor.class);
        DoctorResponse firstResponse = new DoctorResponse(null, "Cardiologie", null);
        DoctorResponse secondResponse = new DoctorResponse(null, "Dermatologie", null);
        when(doctorRepository.findAllByUserEnabledTrueAndVerificationStatusOrderBySpecialtyAsc(
                        DoctorVerificationStatus.VERIFIED))
                .thenReturn(List.of(first, second));
        when(doctorMapper.toResponse(first)).thenReturn(firstResponse);
        when(doctorMapper.toResponse(second)).thenReturn(secondResponse);

        assertThat(doctorService.findAll()).containsExactly(firstResponse, secondResponse);
    }
}
