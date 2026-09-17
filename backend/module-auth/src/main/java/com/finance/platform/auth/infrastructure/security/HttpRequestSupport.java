package com.finance.platform.auth.infrastructure.security;

import jakarta.servlet.http.HttpServletRequest;

public final class HttpRequestSupport {

	private HttpRequestSupport() {
	}

	public static String resolveClientIp(HttpServletRequest request) {
		if (request == null) {
			return "unknown";
		}
		String forwarded = request.getHeader("X-Forwarded-For");
		if (forwarded != null && !forwarded.isBlank()) {
			int comma = forwarded.indexOf(',');
			return comma > 0 ? forwarded.substring(0, comma).trim() : forwarded.trim();
		}
		String remote = request.getRemoteAddr();
		return remote == null || remote.isBlank() ? "unknown" : remote;
	}
}
