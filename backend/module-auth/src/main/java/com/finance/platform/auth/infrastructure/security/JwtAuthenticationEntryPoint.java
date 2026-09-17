package com.finance.platform.auth.infrastructure.security;

import com.finance.platform.core.observability.ObservabilityMdc;
import com.finance.platform.core.observability.SecurityEventLogger;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

@Component
@RequiredArgsConstructor
public class JwtAuthenticationEntryPoint implements AuthenticationEntryPoint {

	private final SecurityEventLogger securityEventLogger;

	@Override
	public void commence(
			HttpServletRequest request,
			HttpServletResponse response,
			AuthenticationException authException
	) throws IOException {
		securityEventLogger.authenticationRequired(request.getRequestURI(), request.getMethod());
		response.setStatus(HttpStatus.UNAUTHORIZED.value());
		response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
		String referenceId = ObservabilityMdc.currentRequestId();
		String body = """
				{"type":"about:blank","title":"Unauthorized","status":401,"detail":"Authentication required or token invalid.","referenceId":"%s"}
				""".formatted(referenceId);
		response.getOutputStream().write(body.getBytes(StandardCharsets.UTF_8));
	}
}
