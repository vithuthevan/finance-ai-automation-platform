package com.finance.platform.auth.application.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.List;
import java.util.UUID;

public record CreateUserRequest(
		@NotBlank @Email String email,
		@NotBlank @Size(min = 8, max = 100) String password,
		@NotBlank @Size(max = 200) String fullName,
		@NotBlank String role,
		List<UUID> clientIds
) {
}
