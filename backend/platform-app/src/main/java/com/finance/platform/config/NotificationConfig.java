package com.finance.platform.config;

import com.finance.platform.auth.infrastructure.persistence.UserJpaRepository;
import com.finance.platform.core.notification.NotificationDispatcher;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class NotificationConfig {

	@Bean
	NotificationDispatcher.UserEmailLookup userEmailLookup(UserJpaRepository userRepository) {
		return userId -> userRepository.findById(userId)
				.filter(user -> user.getDeletedAt() == null)
				.map(user -> user.getEmail());
	}
}
