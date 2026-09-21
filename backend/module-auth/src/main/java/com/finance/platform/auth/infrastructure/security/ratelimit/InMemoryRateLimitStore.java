package com.finance.platform.auth.infrastructure.security.ratelimit;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
@ConditionalOnProperty(name = "app.auth.rate-limit-store", havingValue = "memory", matchIfMissing = true)
public class InMemoryRateLimitStore implements RateLimitStore {

	private final Map<String, Deque<Instant>> attempts = new ConcurrentHashMap<>();

	@Override
	public RateLimitDecision tryConsume(String key, RateLimitPolicy policy) {
		String normalized = key == null ? "unknown" : key.trim().toLowerCase();
		Instant now = Instant.now();
		Instant windowStart = now.minusSeconds(policy.windowSeconds());
		Deque<Instant> history = attempts.computeIfAbsent(normalized, ignored -> new ArrayDeque<>());
		synchronized (history) {
			while (!history.isEmpty() && history.peekFirst().isBefore(windowStart)) {
				history.pollFirst();
			}
			if (history.size() >= policy.maxAttempts()) {
				Instant oldest = history.peekFirst();
				long retryAfter = oldest == null
						? policy.windowSeconds()
						: Math.max(1, policy.windowSeconds() - (now.getEpochSecond() - oldest.getEpochSecond()));
				return RateLimitDecision.deny(retryAfter);
			}
			history.addLast(now);
			return RateLimitDecision.allow();
		}
	}
}
