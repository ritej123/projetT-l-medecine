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

    @Test
    void doesNotExposeDataIntegrityDetailsToClients() {
        var response = handler.handleDataIntegrity(
                new DataIntegrityViolationException("database detail containing sensitive data"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().message())
                .isEqualTo("La ressource existe déjà ou viole une contrainte.")
                .doesNotContain("database detail");
    }

    @Test
    void returnsForbiddenForDeniedResourceAccess() {
        var response = handler.handleAccessDenied(new AccessDeniedException("not allowed"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void returnsSafeReasonForDeniedTeleconsultationAccess() {
        var response = handler.handleTeleconsultationAccessDenied(
                new TeleconsultationAccessDeniedException("Le rendez-vous n’est pas confirmé."));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().message()).isEqualTo("Le rendez-vous n’est pas confirmé.");
    }
}
