package com.finance.platform.core.notification;

public interface EmailService {

	void send(String to, String subject, String body);

	default void send(String to, String subject, String body, EmailAttachment attachment) {
		send(to, subject, body);
	}
}
