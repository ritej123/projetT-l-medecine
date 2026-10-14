package com.telemed.platform.practitioner.service;

import java.util.List;

import com.telemed.platform.practitioner.api.PractitionerResponse;
import com.telemed.platform.practitioner.mapper.PractitionerMapper;
import com.telemed.platform.practitioner.repository.PractitionerRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PractitionerService {

    private final PractitionerRepository practitionerRepository;
    private final PractitionerMapper practitionerMapper;

    public PractitionerService(PractitionerRepository practitionerRepository, PractitionerMapper practitionerMapper) {
        this.practitionerRepository = practitionerRepository;
        this.practitionerMapper = practitionerMapper;
    }

    @Transactional(readOnly = true)
    public List<PractitionerResponse> findAll() {
        return practitionerRepository.findAllByOrderByDisplayNameAsc().stream()
                .map(practitionerMapper::toResponse)
                .toList();
    }
}
