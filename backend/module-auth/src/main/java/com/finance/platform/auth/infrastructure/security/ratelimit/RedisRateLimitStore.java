package com.finance.platform.auth.infrastructure.security.ratelimit;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Component
@ConditionalOnProperty(name = "app.auth.rate-limit-store", havingValue = "redis")
public class RedisRateLimitStore implements RateLimitStore {

	private static final String KEY_PREFIX = "auth:ratelimit:";

	private final StringRedisTemplate redis;

	public RedisRateLimitStore(StringRedisTemplate redis) {
		this.redis = redis;
	}

	@Override
	public RateLimitDecision tryConsume(String key, RateLimitPolicy policy) {
		String normalized = key == null ? "unknown" : key.trim().toLowerCase();
		String redisKey = KEY_PREFIX + normalized;
		long now = Instant.now().getEpochSecond();
		long windowStart = now - policy.windowSeconds();

		var zSet = redis.opsForZSet();
		zSet.removeRangeByScore(redisKey, 0, windowStart);
		Long count = zSet.size(redisKey);
		if (count != null && count >= policy.maxAttempts()) {
			Double oldestScore = zSet.rangeWithScores(redisKey, 0, 0).stream()
					.findFirst()
					.map(org.springframework.data.redis.core.ZSetOperations.TypedTuple::getScore)
					.orElse(null);
			long retryAfter = oldestScore == null
					? policy.windowSeconds()
					: Math.max(1, policy.windowSeconds() - (now - oldestScore.longValue()));
			return RateLimitDecision.deny(retryAfter);
		}
		zSet.add(redisKey, UUID.randomUUID().toString(), now);
		redis.expire(redisKey, policy.windowSeconds() + 5, TimeUnit.SECONDS);
		return RateLimitDecision.allow();
	}
}
