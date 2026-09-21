package com.finance.platform.auth.application.service;

import com.finance.platform.auth.domain.model.EmailVerificationToken;
import com.finance.platform.auth.domain.model.User;
import com.finance.platform.auth.infrastructure.persistence.EmailVerificationTokenJpaRepository;
import com.finance.platform.auth.infrastructure.persistence.UserJpaRepository;
import com.finance.platform.auth.infrastructure.security.AuthProperties;
import com.finance.platform.core.audit.AuditAction;
import com.finance.platform.core.audit.AuditEvent;
import com.finance.platform.core.audit.AuditLogger;
import com.finance.platform.core.audit.AuditResourceType;
import com.finance.platform.core.exception.ValidationException;
import com.finance.platform.core.observability.SecurityEventLogger;
import com.finance.platform.core.notification.EmailService;
import com.finance.platform.core.notification.EmailTemplateService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class EmailVerificationService {

	private final EmailVerificationTokenJpaRepository verificationTokenRepository;
	private final UserJpaRepository userRepository;
	private final EmailService emailService;
	private final EmailTemplateService emailTemplateService;
	private final AuthProperties authProperties;
	private final AuditLogger auditLogger;
	private final SecurityEventLogger securityEventLogger;

	@Transactional
	public void markVerifiedIfNotRequired(User user) {
		if (!authProperties.isEmailVerificationRequired()) {
			user.setEmailVerifiedAt(Instant.now());
			userRepository.save(user);
		}
	}

	@Transactional
	public void sendVerificationEmail(User user) {
		if (!authProperties.isEmailVerificationRequired() || user.getEmailVerifiedAt() != null) {
			return;
		}
		invalidateUnusedTokens(user.getId());
		String raw = randomToken();
		verificationTokenRepository.save(EmailVerificationToken.builder()
				.user(user)
				.tokenHash(sha256(raw))
				.expiresAt(Instant.now().plus(48, ChronoUnit.HOURS))
				.build());
		String link = emailTemplateService.absolute("/verify-email?token=" + raw);
		emailService.send(user.getEmail(), "Verify your email",
				"Please verify your email address to activate your account.\n\nVerify: " + link
						+ "\n\nThis link expires in 48 hours.");
	}

	@Transactional
	public void resendVerification(String email) {
		if (email == null || email.isBlank()) {
			return;
		}
		userRepository.findByEmailAndDeletedAtIsNull(email.trim()).ifPresent(user -> {
			if (user.getEmailVerifiedAt() != null || !authProperties.isEmailVerificationRequired()) {
				return;
			}
			sendVerificationEmail(user);
		});
	}

	@Transactional
	public void verifyEmail(String token) {
		EmailVerificationToken stored = verificationTokenRepository.findByTokenHash(sha256(token))
				.orElseThrow(() -> new ValidationException("token", "Verification token is invalid"));
		if (!stored.isUsable()) {
			throw new ValidationException("token", "Verification token is expired");
		}
		User user = stored.getUser();
		user.setEmailVerifiedAt(Instant.now());
		userRepository.save(user);
		stored.setUsedAt(Instant.now());
		verificationTokenRepository.save(stored);
		invalidateUnusedTokens(user.getId());

		Map<String, Object> metadata = new LinkedHashMap<>();
		metadata.put("email", user.getEmail());
		securityEventLogger.emailVerified(user.getId(), user.getFirmId(), user.getEmail());
		auditLogger.record(AuditEvent.builder()
				.firmId(user.getFirmId())
				.actorUserId(user.getId())
				.actorRole(user.getRole().getCode().name())
				.action(AuditAction.EMAIL_VERIFIED)
				.resourceType(AuditResourceType.AUTH)
				.resourceId(user.getId())
				.metadata(metadata)
				.build());
	}

	public boolean isLoginAllowed(User user) {
		if (!authProperties.isEmailVerificationRequired()) {
			return true;
		}
		return user.getEmailVerifiedAt() != null;
	}

	private void invalidateUnusedTokens(java.util.UUID userId) {
		for (EmailVerificationToken token : verificationTokenRepository.findByUser_IdAndUsedAtIsNull(userId)) {
			token.setUsedAt(Instant.now());
			verificationTokenRepository.save(token);
		}
	}

	private static String randomToken() {
		byte[] bytes = new byte[32];
		new SecureRandom().nextBytes(bytes);
		return HexFormat.of().formatHex(bytes);
	}

	private static String sha256(String raw) {
		try {
			return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(raw.getBytes(StandardCharsets.UTF_8)));
		} catch (Exception ex) {
			throw new IllegalStateException("SHA-256 unavailable");
		}
	}
}
