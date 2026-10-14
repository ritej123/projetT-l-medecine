package com.telemed.platform.user.api;

import java.util.UUID;

import com.telemed.platform.user.domain.Role;

public record UserResponse(UUID id, String email, Role role) {
}
