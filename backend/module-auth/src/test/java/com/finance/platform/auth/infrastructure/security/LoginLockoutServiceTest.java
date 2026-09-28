package com.finance.platform.auth.infrastructure.security;

import com.finance.platform.core.observability.SecurityEventLogger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.LockedException;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

class LoginLockoutServiceTest {

	private static final Instant START = Instant.parse("2026-01-01T00:00:00Z");

	private final SecurityEventLogger securityEventLogger = mock(SecurityEventLogger.class);
	private MutableClock clock;
	private LoginLockoutService lockoutService;

	@BeforeEach
	void setUp() {
		clock = new MutableClock(START);
		AuthProperties properties = new AuthProperties();
		AuthProperties.LoginLockout policy = new AuthProperties.LoginLockout();
		policy.setMaxFailures(5);
		policy.setFailureWindowSeconds(900);
		policy.setLockoutSeconds(900);
		properties.setLoginLockout(policy);
		lockoutService = new LoginLockoutService(properties, securityEventLogger, clock);
	}

	@Test
	void locksAfterMaxFailures() {
		for (int i = 0; i < 5; i++) {
			lockoutService.recordFailure("user@example.com");
		}
		assertThatThrownBy(() -> lockoutService.assertNotLocked("user@example.com"))
				.isInstanceOf(LockedException.class);
	}

	@Test
	void normalizesEmailForLockoutCounter() {
		for (int i = 0; i < 5; i++) {
			lockoutService.recordFailure(" User@Example.com ");
		}
		assertThatThrownBy(() -> lockoutService.assertNotLocked("user@example.com"))
				.isInstanceOf(LockedException.class);
	}

	@Test
	void clearsLockoutAfterWindow() {
		for (int i = 0; i < 5; i++) {
			lockoutService.recordFailure("user@example.com");
		}
		clock.advanceSeconds(901);
		lockoutService.assertNotLocked("user@example.com");
	}

	@Test
	void concurrentFailuresReachLockThreshold() throws InterruptedException {
		AuthProperties properties = new AuthProperties();
		AuthProperties.LoginLockout policy = new AuthProperties.LoginLockout();
		policy.setMaxFailures(10);
		properties.setLoginLockout(policy);
		lockoutService = new LoginLockoutService(properties, securityEventLogger, clock);

		int threads = 8;
		ExecutorService pool = Executors.newFixedThreadPool(threads);
		CountDownLatch start = new CountDownLatch(1);
		AtomicInteger locked = new AtomicInteger();
		for (int i = 0; i < threads; i++) {
			pool.submit(() -> {
				try {
					start.await();
					for (int attempt = 0; attempt < 3; attempt++) {
						lockoutService.recordFailure("race@example.com");
					}
				} catch (InterruptedException ex) {
					Thread.currentThread().interrupt();
				}
			});
		}
		start.countDown();
		pool.shutdown();
		assertThat(pool.awaitTermination(5, java.util.concurrent.TimeUnit.SECONDS)).isTrue();

		try {
			lockoutService.assertNotLocked("race@example.com");
		} catch (LockedException ex) {
			locked.incrementAndGet();
		}
		assertThat(locked.get()).isEqualTo(1);
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
