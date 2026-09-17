package com.finance.platform.core.observability;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Enriches MDC with tenant context after JWT authentication has run.
 * Registered in the security filter chain after {@code JwtAuthenticationFilter}.
 */
@Component
public class TenantMdcFilter extends OncePerRequestFilter {

	@Override
	protected void doFilterInternal(
			@NonNull HttpServletRequest request,
			@NonNull HttpServletResponse response,
			@NonNull FilterChain filterChain
	) throws ServletException, IOException {
		ObservabilityMdc.enrichFromTenantContext();
		filterChain.doFilter(request, response);
	}
}
