package com.finance.platform.controller.auth;

import com.finance.platform.auth.application.dto.LoginRequest;
import com.finance.platform.auth.application.dto.LoginResponse;
import com.finance.platform.auth.application.service.AuthenticationService;
import com.finance.platform.auth.application.service.SessionService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class LoginController {

	private final AuthenticationService authenticationService;
	private final SessionService sessionService;

	@PostMapping("/login")
	public LoginResponse login(@Valid @RequestBody LoginRequest request) {
		return authenticationService.login(request);
	}

	@PostMapping("/refresh")
	public LoginResponse refresh(@RequestBody TokenRequest request) {
		return sessionService.refresh(request.refreshToken());
	}

	@PostMapping("/logout")
	public void logout(@RequestBody TokenRequest request) {
		sessionService.logout(request.refreshToken());
	}

	@PostMapping("/forgot-password")
	public void forgotPassword(@RequestBody EmailRequest request) {
		sessionService.requestPasswordReset(request.email());
	}

	@PostMapping("/reset-password")
	public void resetPassword(@RequestBody ResetRequest request) {
		sessionService.resetPassword(request.token(), request.newPassword());
	}

	public record TokenRequest(String refreshToken) {
	}

	public record EmailRequest(String email) {
	}

	public record ResetRequest(String token, String newPassword) {
	}
}
