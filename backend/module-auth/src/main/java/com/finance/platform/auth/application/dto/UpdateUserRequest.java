package com.finance.platform.auth.application.dto;

import jakarta.validation.constraints.Size;

public record UpdateUserRequest(
		@Size(max = 200) String fullName,
		@Size(max = 30) String phone,
		String role
) {
}
