package com.finance.platform.core.notification;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@ConditionalOnProperty(name = "app.email.provider", havingValue = "log", matchIfMissing = true)
public class LoggingEmailService implements EmailService {

	@Override
	public void send(String to, String subject, String body) {
		log.info("Email [{}] to {}: {}", subject, to, body);
	}
}
