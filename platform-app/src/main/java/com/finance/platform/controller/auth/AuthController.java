package com.finance.platform.controller.auth;

import com.finance.platform.auth.application.dto.LoginRequest;
import com.finance.platform.auth.application.dto.LoginResponse;
import com.finance.platform.auth.application.dto.RegisterRequest;
import com.finance.platform.auth.application.service.AuthService;
import com.finance.platform.application.service.RegistrationService;
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
public class AuthController {

	private final AuthService authService;
	private final RegistrationService registrationService;

	@PostMapping("/login")
	public LoginResponse login(@Valid @RequestBody LoginRequest request) {
		return authService.login(request);
	}

	@PostMapping("/register")
	@ResponseStatus(HttpStatus.CREATED)
	public LoginResponse register(@Valid @RequestBody RegisterRequest request) {
		return registrationService.registerFirmAdmin(request);
	}
}
