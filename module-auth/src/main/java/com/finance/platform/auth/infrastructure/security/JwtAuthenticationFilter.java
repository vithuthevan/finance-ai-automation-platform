package com.finance.platform.auth.infrastructure.security;

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
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

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
			resolveToken(request).ifPresent(token -> {
				JwtTokenProvider.JwtClaims claims = jwtTokenProvider.parseToken(token);
				userRepository.findById(claims.userId()).ifPresent(user -> {
					SecurityUser securityUser = new SecurityUser(user);
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
							UserRole.valueOf(claims.role().name()),
							clientIds
					));
				});
			});
			filterChain.doFilter(request, response);
		} finally {
			TenantContextHolder.clear();
		}
	}

	private java.util.Optional<String> resolveToken(HttpServletRequest request) {
		String header = request.getHeader(HttpHeaders.AUTHORIZATION);
		if (header != null && header.startsWith("Bearer ")) {
			return java.util.Optional.of(header.substring(7));
		}
		return java.util.Optional.empty();
	}
}
