package com.telemed.platform.practitioner.repository;

import java.util.List;
import java.util.UUID;

import com.telemed.platform.practitioner.domain.Practitioner;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PractitionerRepository extends JpaRepository<Practitioner, UUID> {

    List<Practitioner> findAllByOrderByDisplayNameAsc();
}
