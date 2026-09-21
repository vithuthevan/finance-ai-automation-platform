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
		// Never log body — it may contain password-reset tokens or other secrets.
		int bodyLength = body == null ? 0 : body.length();
		log.info("Email [{}] to {} (bodyLength={})", subject, to, bodyLength);
	}

	@Override
	public void send(String to, String subject, String body, EmailAttachment attachment) {
		int bodyLength = body == null ? 0 : body.length();
		String attachmentName = attachment == null ? null : attachment.filename();
		log.info("Email [{}] to {} (bodyLength={}, attachment={})", subject, to, bodyLength, attachmentName);
	}
}
