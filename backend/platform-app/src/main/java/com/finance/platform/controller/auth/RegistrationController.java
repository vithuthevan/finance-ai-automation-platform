package com.finance.platform.controller.auth;

import com.finance.platform.auth.application.dto.RegisterRequest;
import com.finance.platform.auth.application.dto.RegisterResponse;
import com.finance.platform.auth.infrastructure.security.AuthRateLimiter;
import com.finance.platform.auth.infrastructure.security.HttpRequestSupport;
import com.finance.platform.application.service.RegistrationService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class RegistrationController {

	private final RegistrationService registrationService;
	private final AuthRateLimiter authRateLimiter;

	@PostMapping("/register")
	@ResponseStatus(HttpStatus.CREATED)
	public RegisterResponse register(
			@Valid @RequestBody RegisterRequest request,
			HttpServletRequest httpRequest
	) {
		authRateLimiter.checkAllowed("register:ip:" + HttpRequestSupport.resolveClientIp(httpRequest));
		if (request.email() != null && !request.email().isBlank()) {
			authRateLimiter.checkAllowed("register:" + request.email());
		}
		return registrationService.register(request);
	}
}
