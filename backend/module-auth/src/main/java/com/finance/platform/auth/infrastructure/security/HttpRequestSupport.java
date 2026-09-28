package com.finance.platform.auth.infrastructure.security;

import jakarta.servlet.http.HttpServletRequest;

/**
 * Resolves the client IP for auth abuse controls.
 * <p>
 * Uses {@link HttpServletRequest#getRemoteAddr()} only. With {@code server.forward-headers-strategy=framework}
 * (see {@code application.yml}), Spring applies forwarded headers from trusted proxies when the connection
 * is via a load balancer; client-supplied {@code X-Forwarded-For} on direct access does not affect the result.
 */
public final class HttpRequestSupport {

	private HttpRequestSupport() {
	}

	public static String resolveClientIp(HttpServletRequest request) {
		if (request == null) {
			return "unknown";
		}
		String remote = request.getRemoteAddr();
		return remote == null || remote.isBlank() ? "unknown" : remote.trim();
	}
}
