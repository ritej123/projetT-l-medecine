package com.telemed.platform.practitioner.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.UUID;

import com.telemed.platform.practitioner.api.PractitionerResponse;
import com.telemed.platform.practitioner.domain.Practitioner;
import com.telemed.platform.practitioner.mapper.PractitionerMapper;
import com.telemed.platform.practitioner.repository.PractitionerRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class PractitionerServiceTest {

    @Mock
    private PractitionerRepository practitionerRepository;

    @Mock
    private PractitionerMapper practitionerMapper;

    @InjectMocks
    private PractitionerService practitionerService;

    @Test
    void returnsPractitionersAsResponses() {
        Practitioner practitioner = new Practitioner("Dr. Camille Martin", "Médecine générale");
        PractitionerResponse response = new PractitionerResponse(UUID.randomUUID(), "Dr. Camille Martin",
                "Médecine générale");
        when(practitionerRepository.findAllByOrderByDisplayNameAsc()).thenReturn(List.of(practitioner));
        when(practitionerMapper.toResponse(practitioner)).thenReturn(response);

        assertThat(practitionerService.findAll()).containsExactly(response);
        verify(practitionerRepository).findAllByOrderByDisplayNameAsc();
    }
}
