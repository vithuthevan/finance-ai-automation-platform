package com.finance.platform.config;

import com.finance.platform.auth.infrastructure.security.JwtProperties;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.annotation.Profile;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.util.Set;

@Component
@Profile("prod")
public class ProductionJwtSecretValidator {

	private static final Set<String> WEAK_SECRETS = Set.of(
			"change-me",
			"default-secret",
			"secret",
			"finance-platform",
			"your-secret-here"
	);

	@EventListener(ApplicationReadyEvent.class)
	public void validate(JwtProperties jwtProperties) {
		String secret = jwtProperties.secret();
		if (secret == null || secret.isBlank()) {
			throw new IllegalStateException("APP_JWT_SECRET must be configured in production");
		}
		if (secret.length() < 32 || WEAK_SECRETS.contains(secret.toLowerCase())) {
			throw new IllegalStateException("APP_JWT_SECRET is too weak for production use");
		}
	}
}
