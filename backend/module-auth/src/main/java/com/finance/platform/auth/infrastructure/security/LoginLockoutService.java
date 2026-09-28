package com.finance.platform.auth.infrastructure.security;

import com.finance.platform.core.observability.SecurityEventLogger;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.LockedException;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Tracks failed login attempts and applies a temporary lockout.
 * Lockouts expire automatically; accounts are never permanently locked.
 */
@Component
@RequiredArgsConstructor
public class LoginLockoutService {

	private final AuthProperties authProperties;
	private final SecurityEventLogger securityEventLogger;
	private final Clock clock;

	private final Map<String, FailureState> failures = new ConcurrentHashMap<>();

	public void assertNotLocked(String email) {
		String key = AuthIdentifierNormalizer.normalizeEmail(email);
		FailureState state = failures.get(key);
		if (state == null) {
			return;
		}
		synchronized (state) {
			Instant now = clock.instant();
			state.pruneFailures(now);
			if (state.lockedUntil != null && state.lockedUntil.isAfter(now)) {
				throw new LockedException("Account is temporarily locked");
			}
			if (state.lockedUntil != null && !state.lockedUntil.isAfter(now)) {
				state.lockedUntil = null;
				state.failureTimes.clear();
			}
			evictIfEmpty(key, state);
		}
	}

	public void recordFailure(String email) {
		String key = AuthIdentifierNormalizer.normalizeEmail(email);
		Instant now = clock.instant();
		AuthProperties.LoginLockout policy = authProperties.getLoginLockout();
		FailureState state = failures.computeIfAbsent(key, ignored -> new FailureState());
		synchronized (state) {
			state.pruneFailures(now);
			state.failureTimes.addLast(now);
			if (state.failureTimes.size() >= policy.getMaxFailures() && state.lockedUntil == null) {
				state.lockedUntil = now.plusSeconds(policy.getLockoutSeconds());
				securityEventLogger.accountTemporarilyLocked(key);
			}
		}
	}

	public void clearFailures(String email) {
		failures.remove(AuthIdentifierNormalizer.normalizeEmail(email));
	}

	public void resetAll() {
		failures.clear();
	}

	private void evictIfEmpty(String key, FailureState state) {
		if (state.lockedUntil == null && state.failureTimes.isEmpty()) {
			failures.remove(key, state);
		}
	}

	private final class FailureState {
		private final Deque<Instant> failureTimes = new ArrayDeque<>();
		private Instant lockedUntil;

		private void pruneFailures(Instant now) {
			long windowSeconds = authProperties.getLoginLockout().getFailureWindowSeconds();
			Instant windowStart = now.minusSeconds(windowSeconds);
			while (!failureTimes.isEmpty() && failureTimes.peekFirst().isBefore(windowStart)) {
				failureTimes.pollFirst();
			}
		}
	}
}
