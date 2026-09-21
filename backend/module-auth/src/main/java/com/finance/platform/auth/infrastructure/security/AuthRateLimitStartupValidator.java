package com.finance.platform.auth.infrastructure.security;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import java.util.Arrays;

@Slf4j
@Component
@RequiredArgsConstructor
public class AuthRateLimitStartupValidator implements ApplicationRunner {

	private final AuthProperties authProperties;
	private final Environment environment;

	@Override
	public void run(ApplicationArguments args) {
		if (!authProperties.isRateLimitEnabled()) {
			return;
		}
		boolean memoryStore = !"redis".equalsIgnoreCase(authProperties.getRateLimitStore());
		if (!memoryStore || !authProperties.isMultiInstanceDeployment()) {
			return;
		}
		String message = "Auth rate limiting uses in-memory store while app.auth.multi-instance-deployment=true. "
				+ "Configure app.auth.rate-limit-store=redis for horizontal scaling.";
		if (isProdProfile() && authProperties.isFailOnInMemoryMultiInstance()) {
			throw new IllegalStateException(message);
		}
		log.warn(message);
	}

	private boolean isProdProfile() {
		return Arrays.stream(environment.getActiveProfiles()).anyMatch(p -> "prod".equalsIgnoreCase(p));
	}
}
