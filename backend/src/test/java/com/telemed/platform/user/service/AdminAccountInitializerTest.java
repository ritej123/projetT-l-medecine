package com.telemed.platform.user.service;

import static org.mockito.ArgumentMatchers.any;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.telemed.platform.user.domain.User;
import com.telemed.platform.user.domain.Role;
import com.telemed.platform.user.repository.UserRepository;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.security.crypto.password.PasswordEncoder;

@ExtendWith(MockitoExtension.class)
class AdminAccountInitializerTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Test
    void createsBootstrapAdminWhenConfigured() throws Exception {
        when(userRepository.findByEmail("admin@example.test")).thenReturn(Optional.empty());
        when(passwordEncoder.encode("long-enough-password")).thenReturn("bcrypt-hash");

        new AdminAccountInitializer(
                userRepository, passwordEncoder, "ADMIN@example.test", "long-enough-password")
                .run(new DefaultApplicationArguments(new String[0]));

        verify(userRepository).save(any(User.class));
    }

    @Test
    void skipsBootstrapWhenCredentialsAreNotConfigured() throws Exception {
        new AdminAccountInitializer(userRepository, passwordEncoder, "", "")
                .run(new DefaultApplicationArguments(new String[0]));

        verify(userRepository, never()).save(any(User.class));
    }

    @Test
    void refusesToElevateAnExistingNonAdminAccount() {
        when(userRepository.findByEmail("admin@example.test"))
                .thenReturn(Optional.of(new User("admin@example.test", "encoded-password", Role.PATIENT)));

        assertThatThrownBy(() -> new AdminAccountInitializer(
                        userRepository, passwordEncoder, "admin@example.test", "long-enough-password")
                .run(new DefaultApplicationArguments(new String[0])))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("non-administrator");

        verify(userRepository, never()).save(any(User.class));
    }
}
