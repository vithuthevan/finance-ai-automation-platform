package com.finance.platform.auth.infrastructure.security.ratelimit;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class InMemoryRateLimitStoreTest {

	private final InMemoryRateLimitStore store = new InMemoryRateLimitStore();

	@Test
	void enforcesLimitWithinWindow() {
		RateLimitPolicy policy = new RateLimitPolicy(3, 60);
		assertThat(store.tryConsume("login:test@example.com", policy).allowed()).isTrue();
		assertThat(store.tryConsume("login:test@example.com", policy).allowed()).isTrue();
		assertThat(store.tryConsume("login:test@example.com", policy).allowed()).isTrue();
		RateLimitDecision denied = store.tryConsume("login:test@example.com", policy);
		assertThat(denied.allowed()).isFalse();
		assertThat(denied.retryAfterSeconds()).isGreaterThan(0);
	}

	@Test
	void expiresOldAttempts() throws InterruptedException {
		RateLimitPolicy policy = new RateLimitPolicy(1, 1);
		assertThat(store.tryConsume("reset:ip:1.2.3.4", policy).allowed()).isTrue();
		Thread.sleep(1100);
		assertThat(store.tryConsume("reset:ip:1.2.3.4", policy).allowed()).isTrue();
	}
}
