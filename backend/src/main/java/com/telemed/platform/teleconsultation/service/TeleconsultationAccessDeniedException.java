package com.telemed.platform.teleconsultation.service;

import org.springframework.security.access.AccessDeniedException;

public class TeleconsultationAccessDeniedException extends AccessDeniedException {

    public TeleconsultationAccessDeniedException(String message) {
        super(message);
    }
}
