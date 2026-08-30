package com.finance.platform.auth.application.service;

import com.finance.platform.auth.application.dto.LoginResponse;
import com.finance.platform.auth.domain.model.PasswordResetToken;
import com.finance.platform.auth.domain.model.RefreshToken;
import com.finance.platform.auth.domain.model.User;
import com.finance.platform.auth.infrastructure.persistence.PasswordResetTokenJpaRepository;
import com.finance.platform.auth.infrastructure.persistence.RefreshTokenJpaRepository;
import com.finance.platform.auth.infrastructure.persistence.UserJpaRepository;
import com.finance.platform.core.exception.BusinessException;
import com.finance.platform.core.exception.ValidationException;
import com.finance.platform.core.notification.EmailService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HexFormat;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class SessionService {

	private final RefreshTokenJpaRepository refreshTokenRepository;
	private final PasswordResetTokenJpaRepository passwordResetTokenRepository;
	private final UserJpaRepository userRepository;
	private final JwtService jwtService;
	private final PasswordEncoder passwordEncoder;
	private final EmailService emailService;
	private final com.finance.platform.auth.api.UserFacade userFacade;

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
		RefreshToken stored = refreshTokenRepository.findByTokenHash(sha256(refreshToken))
				.filter(RefreshToken::isActive)
				.orElseThrow(() -> new BusinessException("Refresh token is invalid or expired"));
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
		});
	}

	@Transactional
	public void requestPasswordReset(String email) {
		userRepository.findByEmailAndDeletedAtIsNull(email).ifPresent(user -> {
			String raw = randomToken();
			passwordResetTokenRepository.save(PasswordResetToken.builder()
					.user(user)
					.tokenHash(sha256(raw))
					.expiresAt(Instant.now().plus(2, ChronoUnit.HOURS))
					.build());
			emailService.send(user.getEmail(), "Password reset", "Use this reset token: " + raw);
		});
	}

	@Transactional
	public void resetPassword(String token, String newPassword) {
		PasswordResetToken stored = passwordResetTokenRepository.findByTokenHash(sha256(token))
				.orElseThrow(() -> new ValidationException("token", "Reset token is invalid"));
		if (stored.getUsedAt() != null || stored.getExpiresAt().isBefore(Instant.now())) {
			throw new ValidationException("token", "Reset token is expired");
		}
		if (newPassword == null || newPassword.length() < 8) {
			throw new ValidationException("newPassword", "Password must be at least 8 characters");
		}
		User user = stored.getUser();
		user.setPasswordHash(passwordEncoder.encode(newPassword));
		userRepository.save(user);
		stored.setUsedAt(Instant.now());
		passwordResetTokenRepository.save(stored);
		refreshTokenRepository.deleteByUser_Id(user.getId());
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
