package com.finance.platform.auth.infrastructure.security;

import com.finance.platform.core.exception.RateLimitedException;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class AuthRateLimiter {

	private static final int MAX_ATTEMPTS = 20;
	private static final long WINDOW_SECONDS = 60;

	private final Map<String, Deque<Instant>> attempts = new ConcurrentHashMap<>();

	public void checkAllowed(String key) {
		String normalized = key == null ? "unknown" : key.trim().toLowerCase();
		Instant now = Instant.now();
		Instant windowStart = now.minusSeconds(WINDOW_SECONDS);
		Deque<Instant> history = attempts.computeIfAbsent(normalized, ignored -> new ArrayDeque<>());
		synchronized (history) {
			while (!history.isEmpty() && history.peekFirst().isBefore(windowStart)) {
				history.pollFirst();
			}
			if (history.size() >= MAX_ATTEMPTS) {
				throw new com.finance.platform.core.exception.RateLimitedException(
						"Too many requests. Please try again later.");
			}
			history.addLast(now);
		}
	}
}
