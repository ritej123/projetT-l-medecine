package com.telemed.platform.common.exception;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Set;

import jakarta.validation.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.access.AccessDeniedException;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import com.telemed.platform.teleconsultation.service.TeleconsultationAccessDeniedException;

class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    void returnsBadRequestForConstraintViolationsOnRequestParameters() {
        var response = handler.handleMalformedRequest(new ConstraintViolationException(Set.of()));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }
}
