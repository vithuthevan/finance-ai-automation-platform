package com.finance.platform.auth.application.dto;

import java.util.UUID;

public record LoginResponse(UUID userId, String email, String fullName) {
}
