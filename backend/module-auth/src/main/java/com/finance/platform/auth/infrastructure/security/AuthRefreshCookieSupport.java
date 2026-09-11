package com.finance.platform.auth.infrastructure.security;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

@Component
public class AuthRefreshCookieSupport {

	public static final String COOKIE_NAME = "fp_refresh";
	public static final String COOKIE_PATH = "/api/v1/auth";

	private final boolean secure;
	private final List<String> allowedOrigins;

	public AuthRefreshCookieSupport(
			@Value("${app.auth.cookie.secure:false}") boolean secure,
			@Value("${app.cors.allowed-origins:}") String allowedOrigins
	) {
		this.secure = secure;
		this.allowedOrigins = Arrays.stream(allowedOrigins.split(","))
				.map(String::trim)
				.filter(origin -> !origin.isEmpty())
				.toList();
	}

	public void setRefreshCookie(HttpServletResponse response, String rawRefreshToken) {
		ResponseCookie cookie = ResponseCookie.from(COOKIE_NAME, rawRefreshToken)
				.httpOnly(true)
				.secure(secure)
				.sameSite("Lax")
				.path(COOKIE_PATH)
				.maxAge(Duration.ofDays(14))
				.build();
		response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
	}

	public void clearRefreshCookie(HttpServletResponse response) {
		ResponseCookie cookie = ResponseCookie.from(COOKIE_NAME, "")
				.httpOnly(true)
				.secure(secure)
				.sameSite("Lax")
				.path(COOKIE_PATH)
				.maxAge(Duration.ZERO)
				.build();
		response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
	}

	public Optional<String> readRefreshCookie(HttpServletRequest request) {
		Cookie[] cookies = request.getCookies();
		if (cookies == null) {
			return Optional.empty();
		}
		return Arrays.stream(cookies)
				.filter(cookie -> COOKIE_NAME.equals(cookie.getName()))
				.map(Cookie::getValue)
				.filter(value -> value != null && !value.isBlank())
				.findFirst();
	}

	/**
	 * Reject cross-site cookie auth posts when an Origin is present and not allowlisted.
	 * Same-origin (null/empty Origin, e.g. non-browser or same host) is allowed.
	 */
	public void assertOriginAllowed(HttpServletRequest request) {
		String origin = request.getHeader(HttpHeaders.ORIGIN);
		if (origin == null || origin.isBlank()) {
			return;
		}
		if (allowedOrigins.isEmpty()) {
			// Same-origin deployments leave CORS empty; browser still sends Origin on cross-site only.
			return;
		}
		if (!allowedOrigins.contains(origin)) {
			throw new org.springframework.security.access.AccessDeniedException("Origin is not allowed");
		}
	}
}
