package com.finance.platform.auth.infrastructure.security;

import org.springframework.security.authentication.LockedException;
import org.springframework.stereotype.Component;

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
public class LoginLockoutService {

	private static final int MAX_FAILURES = 5;
	private static final long FAILURE_WINDOW_SECONDS = 900;
	private static final long LOCKOUT_SECONDS = 900;

	private final Map<String, FailureState> failures = new ConcurrentHashMap<>();

	public void assertNotLocked(String email) {
		String key = normalize(email);
		FailureState state = failures.get(key);
		if (state == null) {
			return;
		}
		synchronized (state) {
			Instant now = Instant.now();
			state.pruneFailures(now);
			if (state.lockedUntil != null && state.lockedUntil.isAfter(now)) {
				throw new LockedException("Account is temporarily locked");
			}
			if (state.lockedUntil != null && !state.lockedUntil.isAfter(now)) {
				state.lockedUntil = null;
				state.failureTimes.clear();
			}
		}
	}

	public void recordFailure(String email) {
		String key = normalize(email);
		Instant now = Instant.now();
		FailureState state = failures.computeIfAbsent(key, ignored -> new FailureState());
		synchronized (state) {
			state.pruneFailures(now);
			state.failureTimes.addLast(now);
			if (state.failureTimes.size() >= MAX_FAILURES) {
				state.lockedUntil = now.plusSeconds(LOCKOUT_SECONDS);
			}
		}
	}

	public void clearFailures(String email) {
		failures.remove(normalize(email));
	}

	private static String normalize(String email) {
		return email == null ? "unknown" : email.trim().toLowerCase();
	}

	private static final class FailureState {
		private final Deque<Instant> failureTimes = new ArrayDeque<>();
		private Instant lockedUntil;

		private void pruneFailures(Instant now) {
			Instant windowStart = now.minusSeconds(FAILURE_WINDOW_SECONDS);
			while (!failureTimes.isEmpty() && failureTimes.peekFirst().isBefore(windowStart)) {
				failureTimes.pollFirst();
			}
		}
	}
}
