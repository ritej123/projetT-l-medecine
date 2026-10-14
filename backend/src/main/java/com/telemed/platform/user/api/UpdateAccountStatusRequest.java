package com.telemed.platform.user.api;

import jakarta.validation.constraints.NotNull;

public record UpdateAccountStatusRequest(@NotNull Boolean enabled) {
}
