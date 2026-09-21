package com.finance.platform.auth.infrastructure.security;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;

@Configuration
@ConditionalOnProperty(name = "app.auth.rate-limit-store", havingValue = "redis")
@EnableConfigurationProperties(AuthProperties.class)
public class AuthRedisRateLimitConfiguration {

	@Bean
	RedisConnectionFactory authRateLimitRedisConnectionFactory(
			@Value("${app.auth.redis.host:localhost}") String host,
			@Value("${app.auth.redis.port:6379}") int port
	) {
		RedisStandaloneConfiguration config = new RedisStandaloneConfiguration(host, port);
		return new LettuceConnectionFactory(config);
	}

	@Bean
	StringRedisTemplate authRateLimitRedisTemplate(RedisConnectionFactory authRateLimitRedisConnectionFactory) {
		return new StringRedisTemplate(authRateLimitRedisConnectionFactory);
	}
}
