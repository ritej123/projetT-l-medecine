package com.telemed.platform.health.service;

import com.telemed.platform.health.api.HealthResponse;
import org.springframework.stereotype.Service;

@Service
public class HealthService {

    public HealthResponse getHealth() {
        return new HealthResponse("UP");
    }
}
