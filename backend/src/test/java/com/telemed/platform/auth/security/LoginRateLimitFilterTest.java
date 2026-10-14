package com.telemed.platform.auth.security;

import static org.assertj.core.api.Assertions.assertThat;
import java.util.concurrent.atomic.AtomicInteger;

import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class LoginRateLimitFilterTest {

    @Test
    void limitsLoginRequestsByRemoteAddress() throws Exception {
        LoginRateLimitFilter filter = new LoginRateLimitFilter();
        AtomicInteger forwardedRequests = new AtomicInteger();
        FilterChain chain = (request, response) -> forwardedRequests.incrementAndGet();

        for (int attempt = 0; attempt < 6; attempt++) {
            MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/auth/login");
            request.setServletPath("/api/v1/auth/login");
            request.setRemoteAddr("192.0.2.10");
            MockHttpServletResponse response = new MockHttpServletResponse();
            filter.doFilter(request, response, chain);

            if (attempt < 5) {
                assertThat(response.getStatus()).isEqualTo(200);
            } else {
                assertThat(response.getStatus()).isEqualTo(429);
                assertThat(response.getContentAsString()).contains("Trop de tentatives");
            }
        }

        assertThat(forwardedRequests).hasValue(5);
    }

    @Test
    void limitsPatientRegistrationRequestsByRemoteAddress() throws Exception {
        LoginRateLimitFilter filter = new LoginRateLimitFilter();
        AtomicInteger forwardedRequests = new AtomicInteger();
        FilterChain chain = (request, response) -> forwardedRequests.incrementAndGet();

        for (int attempt = 0; attempt < 6; attempt++) {
            MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/auth/register");
            request.setServletPath("/api/v1/auth/register");
            request.setRemoteAddr("192.0.2.11");
            MockHttpServletResponse response = new MockHttpServletResponse();
            filter.doFilter(request, response, chain);
            assertThat(response.getStatus()).isEqualTo(attempt < 5 ? 200 : 429);
        }

        assertThat(forwardedRequests).hasValue(5);
    }

    @Test
    void limitsDoctorRegistrationRequestsByRemoteAddress() throws Exception {
        LoginRateLimitFilter filter = new LoginRateLimitFilter();
        AtomicInteger forwardedRequests = new AtomicInteger();
        FilterChain chain = (request, response) -> forwardedRequests.incrementAndGet();

        for (int attempt = 0; attempt < 6; attempt++) {
            MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/auth/register/doctor");
            request.setServletPath("/api/v1/auth/register/doctor");
            request.setRemoteAddr("192.0.2.12");
            MockHttpServletResponse response = new MockHttpServletResponse();
            filter.doFilter(request, response, chain);
            assertThat(response.getStatus()).isEqualTo(attempt < 5 ? 200 : 429);
        }

        assertThat(forwardedRequests).hasValue(5);
    }
}
