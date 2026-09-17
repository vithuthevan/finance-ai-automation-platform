package com.finance.platform.auth.infrastructure.security;

import com.finance.platform.core.idempotency.IdempotencyFilter;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.ProviderManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
@EnableConfigurationProperties({JwtProperties.class, AuthProperties.class})
@RequiredArgsConstructor
public class AuthSecurityConfig {

	private final JwtAuthenticationFilter jwtAuthenticationFilter;
	private final com.finance.platform.core.observability.TenantMdcFilter tenantMdcFilter;
	private final IdempotencyFilter idempotencyFilter;
	private final JwtAuthenticationEntryPoint authenticationEntryPoint;
	private final UserDetailsService userDetailsService;
	private final PasswordEncoder passwordEncoder;

	@Bean
	FilterRegistrationBean<IdempotencyFilter> disableServletIdempotencyFilter(IdempotencyFilter filter) {
		FilterRegistrationBean<IdempotencyFilter> registration = new FilterRegistrationBean<>(filter);
		registration.setEnabled(false);
		return registration;
	}

	@Bean
	FilterRegistrationBean<com.finance.platform.core.observability.TenantMdcFilter> disableServletTenantMdcFilter(
			com.finance.platform.core.observability.TenantMdcFilter filter) {
		FilterRegistrationBean<com.finance.platform.core.observability.TenantMdcFilter> registration =
				new FilterRegistrationBean<>(filter);
		registration.setEnabled(false);
		return registration;
	}

	@Bean
	AuthenticationManager authenticationManager() {
		DaoAuthenticationProvider provider = new DaoAuthenticationProvider(userDetailsService);
		provider.setPasswordEncoder(passwordEncoder);
		return new ProviderManager(provider);
	}

	@Bean
	SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
		http
				.csrf(csrf -> csrf.disable())
				.cors(cors -> {
				})
				.headers(headers -> headers
						.contentTypeOptions(contentType -> {
						})
						.frameOptions(frame -> frame.deny())
						.httpStrictTransportSecurity(hsts -> hsts
								.includeSubDomains(true)
								.maxAgeInSeconds(31536000))
						.referrerPolicy(referrer -> referrer.policy(
								org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter.ReferrerPolicy.STRICT_ORIGIN_WHEN_CROSS_ORIGIN))
				)
				.sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
				.exceptionHandling(ex -> ex.authenticationEntryPoint(authenticationEntryPoint))
				.authorizeHttpRequests(auth -> auth
						.requestMatchers(HttpMethod.POST, SecurityPaths.AUTH_LOGIN, SecurityPaths.AUTH_REGISTER,
								SecurityPaths.AUTH_REFRESH, SecurityPaths.AUTH_FORGOT, SecurityPaths.AUTH_RESET,
								SecurityPaths.AUTH_LOGOUT, SecurityPaths.AUTH_VERIFY_EMAIL)
						.permitAll()
						.requestMatchers(SecurityPaths.HEALTH, SecurityPaths.HEALTH + "/**", "/error")
						.permitAll()
						.requestMatchers(SecurityPaths.ACTUATOR_HEALTH, SecurityPaths.ACTUATOR_HEALTH + "/**")
						.permitAll()
						.requestMatchers(SecurityPaths.OPENAPI)
						.permitAll()
						.requestMatchers(SecurityPaths.EXPENSES)
						.authenticated()
						// Future URL-level role rules, e.g.:
						// .requestMatchers(SecurityPaths.EXPENSES).hasAnyRole(SecurityRoles.ADMIN, SecurityRoles.ACCOUNTANT)
						.anyRequest()
						.authenticated()
				)
				.addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class)
				.addFilterAfter(tenantMdcFilter, JwtAuthenticationFilter.class)
				// After JWT so TenantContext is set; servlet auto-registration remains disabled above.
				.addFilterAfter(idempotencyFilter, JwtAuthenticationFilter.class);

		return http.build();
	}
}
