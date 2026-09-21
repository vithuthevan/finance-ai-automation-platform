package com.finance.platform.auth.application.service;

import com.finance.platform.auth.application.dto.LoginResponse;
import com.finance.platform.auth.domain.model.PasswordResetToken;
import com.finance.platform.auth.domain.model.RefreshToken;
import com.finance.platform.auth.domain.model.User;
import com.finance.platform.auth.infrastructure.persistence.PasswordResetTokenJpaRepository;
import com.finance.platform.auth.infrastructure.persistence.RefreshTokenJpaRepository;
import com.finance.platform.auth.infrastructure.persistence.UserJpaRepository;
import com.finance.platform.auth.infrastructure.security.PasswordPolicy;
import com.finance.platform.core.audit.AuditAction;
import com.finance.platform.core.audit.AuditEvent;
import com.finance.platform.core.audit.AuditLogger;
import com.finance.platform.core.audit.AuditOutcome;
import com.finance.platform.core.audit.AuditResourceType;
import com.finance.platform.core.observability.SecurityEventLogger;
import com.finance.platform.core.exception.BusinessException;
import com.finance.platform.core.exception.ValidationException;
import com.finance.platform.core.notification.EmailService;
import com.finance.platform.core.notification.EmailTemplateService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
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
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class SessionService {

	private final RefreshTokenJpaRepository refreshTokenRepository;
	private final PasswordResetTokenJpaRepository passwordResetTokenRepository;
	private final UserJpaRepository userRepository;
	private final JwtService jwtService;
	private final PasswordEncoder passwordEncoder;
	private final EmailService emailService;
	private final EmailTemplateService emailTemplateService;
	private final com.finance.platform.auth.api.UserFacade userFacade;
	private final AuditLogger auditLogger;
	private final SecurityEventLogger securityEventLogger;

	@Transactional
	public String issueRefreshToken(User user) {
		String raw = randomToken();
		RefreshToken token = RefreshToken.builder()
				.user(user)
				.tokenHash(sha256(raw))
				.expiresAt(Instant.now().plus(14, ChronoUnit.DAYS))
				.build();
		refreshTokenRepository.save(token);
		return raw;
	}

	@Transactional
	public LoginResponse refresh(String refreshToken) {
		Optional<RefreshToken> storedOpt = refreshTokenRepository.findByTokenHash(sha256(refreshToken));
		if (storedOpt.isEmpty()) {
			throw new BusinessException("Refresh token is invalid or expired");
		}
		RefreshToken stored = storedOpt.get();
		if (!stored.isActive()) {
			handleRefreshTokenReuse(stored);
			throw new BusinessException("Refresh token is invalid or expired");
		}
		stored.setRevokedAt(Instant.now());
		refreshTokenRepository.save(stored);
		User user = stored.getUser();
		if (user == null || !user.isActive() || user.getDeletedAt() != null) {
			throw new BusinessException(com.finance.platform.core.exception.ErrorCodes.USER_INACTIVE, "User is inactive");
		}
		JwtService.IssuedAccessToken access = jwtService.issueAccessToken(user);
		return new LoginResponse(
				access.accessToken(),
				"Bearer",
				access.expiresInMs(),
				user.getId(),
				user.getEmail(),
				user.getFullName(),
				user.getRole().getCode().name(),
				issueRefreshToken(user),
				userFacade.isUploadOnlyWorkspace(user.getId())
		);
	}

	@Transactional
	public void revokeAllForUser(UUID userId) {
		Instant now = Instant.now();
		for (RefreshToken token : refreshTokenRepository.findByUser_IdAndRevokedAtIsNull(userId)) {
			token.setRevokedAt(now);
			refreshTokenRepository.save(token);
		}
	}

	@Transactional
	public void logout(String refreshToken) {
		refreshTokenRepository.findByTokenHash(sha256(refreshToken)).ifPresent(token -> {
			token.setRevokedAt(Instant.now());
			refreshTokenRepository.save(token);
			User user = token.getUser();
			if (user != null) {
				recordLogout(user);
			}
		});
	}

	@Transactional
	public void requestPasswordReset(String email) {
		userRepository.findByEmailAndDeletedAtIsNull(email).ifPresent(user -> {
			invalidateUnusedPasswordResetTokens(user.getId());
			String raw = randomToken();
			passwordResetTokenRepository.save(PasswordResetToken.builder()
					.user(user)
					.tokenHash(sha256(raw))
					.expiresAt(Instant.now().plus(2, ChronoUnit.HOURS))
					.build());
			String link = emailTemplateService.absolute("/reset-password?token=" + raw);
			emailService.send(user.getEmail(), "Password reset",
					"Use the link below to reset your password. This link expires in 2 hours.\n\nReset: " + link);
			Map<String, Object> metadata = new LinkedHashMap<>();
			metadata.put("email", user.getEmail());
			securityEventLogger.passwordResetRequested(user.getId(), user.getFirmId(), user.getEmail());
			auditLogger.recordIndependent(AuditEvent.builder()
					.firmId(user.getFirmId())
					.actorUserId(user.getId())
					.actorRole(user.getRole().getCode().name())
					.action(AuditAction.PASSWORD_RESET_REQUESTED)
					.resourceType(AuditResourceType.AUTH)
					.resourceId(user.getId())
					.metadata(metadata)
					.build());
		});
	}

	@Transactional
	public void resetPassword(String token, String newPassword) {
		PasswordResetToken stored = passwordResetTokenRepository.findByTokenHash(sha256(token))
				.orElseThrow(() -> new ValidationException("token", "Reset token is invalid"));
		if (stored.getUsedAt() != null || stored.getExpiresAt().isBefore(Instant.now())) {
			throw new ValidationException("token", "Reset token is expired");
		}
		PasswordPolicy.validate(newPassword, "newPassword");
		User user = stored.getUser();
		user.setPasswordHash(passwordEncoder.encode(newPassword));
		userRepository.save(user);
		stored.setUsedAt(Instant.now());
		passwordResetTokenRepository.save(stored);
		revokeAllForUser(user.getId());
		Map<String, Object> metadata = new LinkedHashMap<>();
		metadata.put("email", user.getEmail());
		securityEventLogger.passwordResetCompleted(user.getId(), user.getFirmId(), user.getEmail());
		auditLogger.recordIndependent(AuditEvent.builder()
				.firmId(user.getFirmId())
				.actorUserId(user.getId())
				.actorRole(user.getRole().getCode().name())
				.action(AuditAction.PASSWORD_RESET_COMPLETED)
				.resourceType(AuditResourceType.AUTH)
				.resourceId(user.getId())
				.metadata(metadata)
				.build());
	}

	private void handleRefreshTokenReuse(RefreshToken stored) {
		if (stored.getRevokedAt() == null) {
			return;
		}
		User user = stored.getUser();
		if (user == null) {
			return;
		}
		securityEventLogger.tokenReuseDetected(user.getId(), user.getFirmId(), user.getEmail());
		revokeAllForUser(user.getId());
		Map<String, Object> metadata = new LinkedHashMap<>();
		metadata.put("email", user.getEmail());
		auditLogger.recordIndependent(AuditEvent.builder()
				.firmId(user.getFirmId())
				.actorUserId(user.getId())
				.actorRole(user.getRole().getCode().name())
				.action(AuditAction.TOKEN_REUSE_DETECTED)
				.resourceType(AuditResourceType.AUTH)
				.resourceId(user.getId())
				.metadata(metadata)
				.outcome(AuditOutcome.FAILURE)
				.build());
	}

	private void invalidateUnusedPasswordResetTokens(UUID userId) {
		for (PasswordResetToken token : passwordResetTokenRepository.findByUser_IdAndUsedAtIsNull(userId)) {
			token.setUsedAt(Instant.now());
			passwordResetTokenRepository.save(token);
		}
	}

	private void recordLogout(User user) {
		Map<String, Object> metadata = new LinkedHashMap<>();
		metadata.put("email", user.getEmail());
		securityEventLogger.logout(user.getId(), user.getFirmId(), user.getEmail());
		auditLogger.record(AuditEvent.builder()
				.firmId(user.getFirmId())
				.actorUserId(user.getId())
				.actorRole(user.getRole().getCode().name())
				.action(AuditAction.LOGOUT)
				.resourceType(AuditResourceType.AUTH)
				.resourceId(user.getId())
				.metadata(metadata)
				.build());
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
