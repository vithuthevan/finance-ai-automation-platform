package com.finance.platform.auth.application.service;

import com.finance.platform.auth.application.dto.LoginRequest;
import com.finance.platform.auth.application.dto.LoginResponse;
import com.finance.platform.auth.domain.model.User;
import com.finance.platform.auth.infrastructure.persistence.UserJpaRepository;
import com.finance.platform.auth.infrastructure.security.AuthRateLimiter;
import com.finance.platform.auth.infrastructure.security.LoginLockoutService;
import com.finance.platform.core.audit.AuditAction;
import com.finance.platform.core.audit.AuditEvent;
import com.finance.platform.core.audit.AuditLogger;
import com.finance.platform.core.audit.AuditOutcome;
import com.finance.platform.core.audit.AuditResourceType;
import com.finance.platform.core.exception.BusinessException;
import com.finance.platform.core.observability.SecurityEventLogger;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.LockedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.AuthenticationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class AuthenticationService {

	private final AuthenticationManager authenticationManager;
	private final UserJpaRepository userRepository;
	private final JwtService jwtService;
	private final SessionService sessionService;
	private final AuditLogger auditLogger;
	private final SecurityEventLogger securityEventLogger;
	private final com.finance.platform.auth.api.UserFacade userFacade;
	private final AuthRateLimiter authRateLimiter;
	private final LoginLockoutService loginLockoutService;
	private final EmailVerificationService emailVerificationService;

	@Transactional
	public LoginResponse login(LoginRequest request, String clientIp) {
		authRateLimiter.checkAllowed("login:ip:" + clientIp);
		authRateLimiter.checkAllowed("login:" + request.email());
		Optional<User> existingUser = userRepository.findByEmailAndDeletedAtIsNull(request.email());

		try {
			loginLockoutService.assertNotLocked(request.email());
			authenticationManager.authenticate(
					new UsernamePasswordAuthenticationToken(request.email(), request.password())
			);
		} catch (BadCredentialsException | DisabledException | LockedException ex) {
			loginLockoutService.recordFailure(request.email());
			recordLoginFailure(request.email(), existingUser.orElse(null), ex.getClass().getSimpleName());
			throw new BusinessException("Invalid credentials");
		} catch (AuthenticationException ex) {
			loginLockoutService.recordFailure(request.email());
			recordLoginFailure(request.email(), existingUser.orElse(null), ex.getClass().getSimpleName());
			throw new BusinessException("Invalid credentials");
		}

		User user = existingUser.orElseGet(() -> userRepository.findByEmailAndDeletedAtIsNull(request.email())
				.orElseThrow(() -> new BusinessException("Invalid credentials")));

		if (!emailVerificationService.isLoginAllowed(user)) {
			loginLockoutService.recordFailure(request.email());
			recordLoginFailure(request.email(), user, "EmailNotVerified");
			throw new BusinessException("Invalid credentials");
		}

		loginLockoutService.clearFailures(request.email());
		user.setLastLoginAt(Instant.now());
		userRepository.save(user);
		recordLoginSuccess(user);
		return buildLoginResponse(user);
	}

	private void recordLoginSuccess(User user) {
		Map<String, Object> metadata = new LinkedHashMap<>();
		metadata.put("email", user.getEmail());
		securityEventLogger.loginSuccess(user.getId(), user.getFirmId(), user.getEmail());

		auditLogger.record(AuditEvent.builder()
				.firmId(user.getFirmId())
				.actorUserId(user.getId())
				.actorRole(user.getRole().getCode().name())
				.action(AuditAction.LOGIN_SUCCESS)
				.resourceType(AuditResourceType.AUTH)
				.resourceId(user.getId())
				.metadata(metadata)
				.build());
	}

	private void recordLoginFailure(String email, User user, String reason) {
		Map<String, Object> metadata = new LinkedHashMap<>();
		metadata.put("email", email);
		metadata.put("reason", reason);
		securityEventLogger.loginFailure(email, reason);

		AuditEvent.Builder builder = AuditEvent.builder()
				.action(AuditAction.LOGIN_FAILURE)
				.resourceType(AuditResourceType.AUTH)
				.metadata(metadata)
				.outcome(AuditOutcome.FAILURE);

		if (user != null) {
			builder.firmId(user.getFirmId())
					.actorUserId(user.getId())
					.actorRole(user.getRole().getCode().name())
					.resourceId(user.getId());
		}

		auditLogger.recordIndependent(builder.build());
	}

	private LoginResponse buildLoginResponse(User user) {
		JwtService.IssuedAccessToken issuedToken = jwtService.issueAccessToken(user);

		return new LoginResponse(
				issuedToken.accessToken(),
				"Bearer",
				issuedToken.expiresInMs(),
				user.getId(),
				user.getEmail(),
				user.getFullName(),
				user.getRole().getCode().name(),
				sessionService.issueRefreshToken(user),
				userFacade.isUploadOnlyWorkspace(user.getId())
		);
	}
}
