package com.finance.platform.auth.infrastructure.security.ratelimit;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;

class InMemoryRateLimitStoreTest {

	private MutableClock clock;
	private InMemoryRateLimitStore store;

	@BeforeEach
	void setUp() {
		clock = new MutableClock(Instant.parse("2026-01-01T00:00:00Z"));
		store = new InMemoryRateLimitStore(clock);
	}

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
	void expiresOldAttemptsWhenClockAdvances() {
		RateLimitPolicy policy = new RateLimitPolicy(1, 60);
		assertThat(store.tryConsume("reset:ip:1.2.3.4", policy).allowed()).isTrue();
		clock.advanceSeconds(61);
		assertThat(store.tryConsume("reset:ip:1.2.3.4", policy).allowed()).isTrue();
	}

	@Test
	void purgeRemovesExpiredKeys() {
		RateLimitPolicy policy = new RateLimitPolicy(1, 10);
		store.tryConsume("login:ip:9.9.9.9", policy);
		clock.advanceSeconds(11);
		store.purgeExpiredEntries(policy.windowSeconds());
		clock.advanceSeconds(0);
		assertThat(store.tryConsume("login:ip:9.9.9.9", policy).allowed()).isTrue();
	}

	private static final class MutableClock extends Clock {
		private Instant instant;

		private MutableClock(Instant instant) {
			this.instant = instant;
		}

		void advanceSeconds(long seconds) {
			instant = instant.plusSeconds(seconds);
		}

		@Override
		public ZoneOffset getZone() {
			return ZoneOffset.UTC;
		}

		@Override
		public Clock withZone(java.time.ZoneId zone) {
			return this;
		}

		@Override
		public Instant instant() {
			return instant;
		}
	}
}
