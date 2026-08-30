package com.finance.platform.finance.application.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record UpdateClientRequest(
		@NotBlank(message = "Client name is required")
		@Size(max = 200, message = "Client name must not exceed 200 characters")
		String name,
		@Size(max = 50, message = "Business registration number must not exceed 50 characters")
		String businessRegNo,
		@Email(message = "Contact email must be a valid email address")
		@Size(max = 255, message = "Contact email must not exceed 255 characters")
		String contactEmail
) {
}
