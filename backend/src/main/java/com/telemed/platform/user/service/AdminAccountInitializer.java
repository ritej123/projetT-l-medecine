package com.telemed.platform.user.service;

import java.nio.charset.StandardCharsets;
import java.util.Locale;

import com.telemed.platform.user.domain.Role;
import com.telemed.platform.user.domain.User;
import com.telemed.platform.user.repository.UserRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class AdminAccountInitializer implements ApplicationRunner {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final String email;
    private final String password;

    public AdminAccountInitializer(
            UserRepository userRepository,
            PasswordEncoder passwordEncoder,
            @Value("${app.bootstrap.admin.email:}") String email,
            @Value("${app.bootstrap.admin.password:}") String password) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.email = email;
        this.password = password;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (email.isBlank() && password.isBlank()) {
            return;
        }
        if (email.isBlank() || password.isBlank()
                || password.length() < 12
                || password.getBytes(StandardCharsets.UTF_8).length > 72) {
            throw new IllegalStateException(
                    "Configure both BOOTSTRAP_ADMIN_EMAIL and a 12-72 byte BOOTSTRAP_ADMIN_PASSWORD.");
        }
        String normalizedEmail = email.trim().toLowerCase(Locale.ROOT);
        var existingUser = userRepository.findByEmail(normalizedEmail);
        if (existingUser.isEmpty()) {
            userRepository.save(new User(
                    normalizedEmail, passwordEncoder.encode(password), Role.ADMIN));
        } else if (existingUser.get().getRole() != Role.ADMIN) {
            throw new IllegalStateException(
                    "BOOTSTRAP_ADMIN_EMAIL is already assigned to a non-administrator account.");
        }
    }
}
