package com.telemed.platform.practitioner.api;

import java.util.List;

import com.telemed.platform.practitioner.service.PractitionerService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/practitioners")
public class PractitionerController {

    private final PractitionerService practitionerService;

    public PractitionerController(PractitionerService practitionerService) {
        this.practitionerService = practitionerService;
    }

    @GetMapping
    public List<PractitionerResponse> findAll() {
        return practitionerService.findAll();
    }
}
