package com.finance.platform.core.notification;

import jakarta.mail.Message;
import jakarta.mail.Session;
import jakarta.mail.Transport;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeBodyPart;
import jakarta.mail.internet.MimeMessage;
import jakarta.mail.internet.MimeMultipart;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.util.Properties;

@Slf4j
@Service
@ConditionalOnProperty(name = "app.email.provider", havingValue = "smtp")
public class SmtpEmailService implements EmailService {

	@Value("${app.email.host:}")
	private String host;

	@Value("${app.email.port:587}")
	private int port;

	@Value("${app.email.username:}")
	private String username;

	@Value("${app.email.password:}")
	private String password;

	@Value("${app.email.from:}")
	private String from;

	@Value("${app.email.tls:true}")
	private boolean tls;

	@Override
	public void send(String to, String subject, String body, EmailAttachment attachment) {
		if (attachment == null) {
			send(to, subject, body);
			return;
		}
		if (host == null || host.isBlank()) {
			throw new IllegalStateException("MAIL_HOST is not configured");
		}
		try {
			Properties props = new Properties();
			props.put("mail.smtp.auth", !username.isBlank());
			props.put("mail.smtp.starttls.enable", String.valueOf(tls));
			props.put("mail.smtp.host", host);
			props.put("mail.smtp.port", String.valueOf(port));
			Session session = Session.getInstance(props);
			MimeMessage message = new MimeMessage(session);
			String sender = from == null || from.isBlank() ? username : from;
			message.setFrom(new InternetAddress(sender));
			message.setRecipients(Message.RecipientType.TO, InternetAddress.parse(to, false));
			message.setSubject(subject, "UTF-8");
			MimeBodyPart text = new MimeBodyPart();
			text.setText(body, "UTF-8");
			MimeBodyPart file = new MimeBodyPart();
			file.setFileName(attachment.filename());
			file.setContent(attachment.content(), attachment.contentType());
			MimeMultipart multipart = new MimeMultipart();
			multipart.addBodyPart(text);
			multipart.addBodyPart(file);
			message.setContent(multipart);
			try (Transport transport = session.getTransport("smtp")) {
				if (!username.isBlank()) {
					transport.connect(host, port, username, password);
				} else {
					transport.connect();
				}
				transport.sendMessage(message, message.getAllRecipients());
			}
			log.info("SMTP email with attachment sent to {}", to);
		} catch (Exception ex) {
			throw new IllegalStateException("SMTP delivery failed: " + ex.getMessage(), ex);
		}
	}

	@Override
	public void send(String to, String subject, String body) {
		if (host == null || host.isBlank()) {
			throw new IllegalStateException("MAIL_HOST is not configured");
		}
		try {
			Properties props = new Properties();
			props.put("mail.smtp.auth", !username.isBlank());
			props.put("mail.smtp.starttls.enable", String.valueOf(tls));
			props.put("mail.smtp.host", host);
			props.put("mail.smtp.port", String.valueOf(port));
			Session session = Session.getInstance(props);
			MimeMessage message = new MimeMessage(session);
			String sender = from == null || from.isBlank() ? username : from;
			message.setFrom(new InternetAddress(sender));
			message.setRecipients(Message.RecipientType.TO, InternetAddress.parse(to, false));
			message.setSubject(subject, "UTF-8");
			message.setText(body, "UTF-8");
			try (Transport transport = session.getTransport("smtp")) {
				if (!username.isBlank()) {
					transport.connect(host, port, username, password);
				} else {
					transport.connect();
				}
				transport.sendMessage(message, message.getAllRecipients());
			}
			log.info("SMTP email sent to {}", to);
		} catch (Exception ex) {
			throw new IllegalStateException("SMTP delivery failed: " + ex.getMessage(), ex);
		}
	}
}
