package com.finance.platform.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.web.filter.CorsFilter;

import java.util.Arrays;
import java.util.List;

@Configuration
public class CorsConfig {

	@Bean
	CorsFilter corsFilter(@Value("${app.cors.allowed-origins:}") String allowedOrigins) {
		List<String> origins = Arrays.stream(allowedOrigins.split(","))
				.map(String::trim)
				.filter(origin -> !origin.isEmpty())
				.toList();
		if (origins.stream().anyMatch("*"::equals)) {
			throw new IllegalStateException("Wildcard CORS origins are not allowed when credentials are used. Set APP_CORS_ALLOWED_ORIGINS to explicit origins, or leave empty for same-origin only.");
		}

		CorsConfiguration config = new CorsConfiguration();
		config.setAllowCredentials(true);
		if (!origins.isEmpty()) {
			config.setAllowedOrigins(origins);
		}
		config.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
		config.setAllowedHeaders(List.of(
				"Authorization",
				"Content-Type",
				"Accept",
				"Idempotency-Key",
				"X-Request-Id"
		));
		config.setExposedHeaders(List.of("Content-Disposition"));
		UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
		source.registerCorsConfiguration("/api/**", config);
		return new CorsFilter(source);
	}
}
