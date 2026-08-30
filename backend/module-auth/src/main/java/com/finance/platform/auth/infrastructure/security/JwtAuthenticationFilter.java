package com.finance.platform.auth.infrastructure.security;

import com.finance.platform.auth.domain.model.User;
import com.finance.platform.auth.infrastructure.persistence.UserClientAccessJpaRepository;
import com.finance.platform.auth.infrastructure.persistence.UserJpaRepository;
import com.finance.platform.core.security.TenantContext;
import com.finance.platform.core.security.TenantContextHolder;
import com.finance.platform.core.security.UserRole;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Validates Bearer JWTs on each request and populates Spring Security's context
 * before the request reaches any controller.
 */
@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

	private static final String BEARER_PREFIX = "Bearer ";

	private final JwtTokenProvider jwtTokenProvider;
	private final UserJpaRepository userRepository;
	private final UserClientAccessJpaRepository clientAccessRepository;

	@Override
	protected void doFilterInternal(
			@NonNull HttpServletRequest request,
			@NonNull HttpServletResponse response,
			@NonNull FilterChain filterChain
	) throws ServletException, IOException {
		try {
			resolveBearerToken(request).ifPresent(token -> authenticateIfValid(request, token));
			filterChain.doFilter(request, response);
		} finally {
			SecurityContextHolder.clearContext();
			TenantContextHolder.clear();
		}
	}

	private void authenticateIfValid(HttpServletRequest request, String token) {
		try {
			JwtTokenProvider.JwtClaims claims = jwtTokenProvider.parseToken(token);
			userRepository.findById(claims.userId())
					.filter(this::isActiveUser)
					.filter(user -> user.getFirmId().equals(claims.firmId()))
					.ifPresent(user -> setAuthenticatedUser(request, user));
		} catch (RuntimeException ex) {
			// Invalid or expired token — leave the request unauthenticated.
		}
	}

	private boolean isActiveUser(User user) {
		return user.isActive() && user.getDeletedAt() == null;
	}

	private void setAuthenticatedUser(
			HttpServletRequest request,
			User user
	) {
		SecurityUser securityUser = new SecurityUser(user);
		if (!securityUser.isEnabled()) {
			return;
		}

		UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
				securityUser,
				null,
				securityUser.getAuthorities()
		);
		authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
		SecurityContextHolder.getContext().setAuthentication(authentication);

		var clientIds = clientAccessRepository.findByUser_Id(user.getId()).stream()
				.map(access -> access.getClientId())
				.collect(Collectors.toSet());

		TenantContextHolder.set(new TenantContext(
				user.getId(),
				user.getFirmId(),
				UserRole.valueOf(user.getRole().getCode().name()),
				clientIds
		));
	}

	private Optional<String> resolveBearerToken(HttpServletRequest request) {
		String header = request.getHeader(HttpHeaders.AUTHORIZATION);
		if (header == null || !header.startsWith(BEARER_PREFIX)) {
			return Optional.empty();
		}

		String token = header.substring(BEARER_PREFIX.length()).trim();
		return token.isEmpty() ? Optional.empty() : Optional.of(token);
	}
}
