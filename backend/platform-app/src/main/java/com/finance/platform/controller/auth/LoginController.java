package com.finance.platform.controller.auth;

import com.finance.platform.auth.application.dto.LoginRequest;
import com.finance.platform.auth.application.dto.LoginResponse;
import com.finance.platform.auth.application.service.AuthenticationService;
import com.finance.platform.auth.application.service.SessionService;
import com.finance.platform.auth.infrastructure.security.AuthRefreshCookieSupport;
import com.finance.platform.core.exception.BusinessException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
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
	private final AuthRefreshCookieSupport refreshCookies;

	@PostMapping("/login")
	public LoginResponse login(
			@Valid @RequestBody LoginRequest request,
			HttpServletRequest httpRequest,
			HttpServletResponse httpResponse
	) {
		refreshCookies.assertOriginAllowed(httpRequest);
		LoginResponse issued = authenticationService.login(request);
		refreshCookies.setRefreshCookie(httpResponse, issued.refreshToken());
		return withoutRefreshToken(issued);
	}

	@PostMapping("/refresh")
	public LoginResponse refresh(
			@RequestBody(required = false) TokenRequest request,
			HttpServletRequest httpRequest,
			HttpServletResponse httpResponse
	) {
		refreshCookies.assertOriginAllowed(httpRequest);
		String raw = resolveRefreshToken(request, httpRequest);
		LoginResponse issued = sessionService.refresh(raw);
		refreshCookies.setRefreshCookie(httpResponse, issued.refreshToken());
		return withoutRefreshToken(issued);
	}

	@PostMapping("/logout")
	public void logout(
			@RequestBody(required = false) TokenRequest request,
			HttpServletRequest httpRequest,
			HttpServletResponse httpResponse
	) {
		refreshCookies.assertOriginAllowed(httpRequest);
		resolveRefreshTokenOptional(request, httpRequest).ifPresent(sessionService::logout);
		refreshCookies.clearRefreshCookie(httpResponse);
	}

	@PostMapping("/forgot-password")
	public void forgotPassword(@RequestBody EmailRequest request) {
		sessionService.requestPasswordReset(request.email());
	}

	@PostMapping("/reset-password")
	public void resetPassword(
			@RequestBody ResetRequest request,
			HttpServletResponse httpResponse
	) {
		sessionService.resetPassword(request.token(), request.newPassword());
		refreshCookies.clearRefreshCookie(httpResponse);
	}

	private String resolveRefreshToken(TokenRequest request, HttpServletRequest httpRequest) {
		return resolveRefreshTokenOptional(request, httpRequest)
				.orElseThrow(() -> new BusinessException("Refresh token is invalid or expired"));
	}

	private java.util.Optional<String> resolveRefreshTokenOptional(TokenRequest request, HttpServletRequest httpRequest) {
		if (request != null && request.refreshToken() != null && !request.refreshToken().isBlank()) {
			return java.util.Optional.of(request.refreshToken().trim());
		}
		return refreshCookies.readRefreshCookie(httpRequest);
	}

	private static LoginResponse withoutRefreshToken(LoginResponse issued) {
		return new LoginResponse(
				issued.accessToken(),
				issued.tokenType(),
				issued.expiresIn(),
				issued.userId(),
				issued.email(),
				issued.fullName(),
				issued.role(),
				null,
				issued.uploadOnly()
		);
	}

	public record TokenRequest(String refreshToken) {
	}

	public record EmailRequest(String email) {
	}

	public record ResetRequest(String token, String newPassword) {
	}
}
