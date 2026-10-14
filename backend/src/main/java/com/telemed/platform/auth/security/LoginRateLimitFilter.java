package com.telemed.platform.auth.security;

import java.io.IOException;
import java.time.Duration;
import java.util.concurrent.TimeUnit;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.http.HttpStatus;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
public class LoginRateLimitFilter extends OncePerRequestFilter {

    private static final String LOGIN_PATH = "/api/v1/auth/login";
    private static final String REGISTER_PATH = "/api/v1/auth/register";
    private static final String DOCTOR_REGISTER_PATH = "/api/v1/auth/register/doctor";
    private static final int MAX_ATTEMPTS = 5;
    private static final long WINDOW_MILLIS = Duration.ofMinutes(1).toMillis();

    private final Cache<String, AttemptWindow> attempts = Caffeine.newBuilder()
            .maximumSize(20_000)
            .expireAfterAccess(Duration.ofMinutes(2))
            .build();

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getServletPath();
        return !"POST".equalsIgnoreCase(request.getMethod())
                || (!LOGIN_PATH.equals(path)
                        && !REGISTER_PATH.equals(path)
                        && !DOCTOR_REGISTER_PATH.equals(path));
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        String clientAddress = request.getRemoteAddr();
        AttemptWindow window = attempts.get(clientAddress, ignored -> new AttemptWindow());
        if (!window.tryAcquire(System.currentTimeMillis())) {
            response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
            response.setContentType("application/json");
            response.setCharacterEncoding("UTF-8");
            response.setHeader("Retry-After", Long.toString(TimeUnit.MILLISECONDS.toSeconds(WINDOW_MILLIS)));
            response.getWriter().write(
                    "{\"status\":429,\"error\":\"Too Many Requests\",\"message\":\"Trop de tentatives. Réessayez plus tard.\"}");
            return;
        }
        filterChain.doFilter(request, response);
    }

    private static final class AttemptWindow {
        private long startedAt = System.currentTimeMillis();
        private int count;

        synchronized boolean tryAcquire(long now) {
            if (now - startedAt >= WINDOW_MILLIS) {
                startedAt = now;
                count = 0;
            }
            if (count >= MAX_ATTEMPTS) {
                return false;
            }
            count++;
            return true;
        }
    }
}
