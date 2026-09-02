package com.finance.platform.platformadmin;

import com.finance.platform.auth.domain.model.User;
import com.finance.platform.auth.infrastructure.persistence.UserJpaRepository;
import com.finance.platform.auth.infrastructure.security.SecurityUser;
import com.finance.platform.auth.infrastructure.security.SecurityUtils;
import com.finance.platform.core.audit.AuditAction;
import com.finance.platform.core.audit.AuditEvent;
import com.finance.platform.core.audit.AuditLogger;
import com.finance.platform.core.audit.AuditResourceType;
import com.finance.platform.core.exception.BusinessException;
import com.finance.platform.core.exception.ErrorCodes;
import com.finance.platform.core.exception.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

@Service("platformAdminService")
@RequiredArgsConstructor
public class PlatformAdminService {

	private final PlatformAdminGrantJpaRepository grantRepository;
	private final UserJpaRepository userRepository;
	private final AuditLogger auditLogger;

	public boolean isCurrentUserPlatformAdmin() {
		SecurityUser user = SecurityUtils.requireCurrentUser();
		return isPlatformAdmin(user.getId());
	}

	@Transactional(readOnly = true)
	public boolean isPlatformAdmin(UUID userId) {
		return grantRepository.existsByUserIdAndActiveTrue(userId);
	}

	@Transactional
	public PlatformAdminGrant grant(UUID userId, UUID grantedBy, String reason) {
		User user = userRepository.findById(userId)
				.filter(u -> u.getDeletedAt() == null)
				.orElseThrow(() -> new ResourceNotFoundException("User", userId));

		PlatformAdminGrant grant = grantRepository.findByUserId(userId).orElseGet(() -> PlatformAdminGrant.builder()
				.userId(userId)
				.build());
		grant.setActive(true);
		grant.setGrantedAt(Instant.now());
		grant.setGrantedBy(grantedBy);
		grant.setRevokedAt(null);
		grant.setRevokedBy(null);
		grant.setReason(reason);
		PlatformAdminGrant saved = grantRepository.save(grant);

		Map<String, Object> after = new LinkedHashMap<>();
		after.put("userId", userId.toString());
		after.put("email", user.getEmail());
		after.put("reason", reason == null ? "" : reason);
		auditLogger.record(AuditEvent.builder()
				.actorUserId(grantedBy)
				.action(AuditAction.PLATFORM_ADMIN_GRANTED)
				.resourceType(AuditResourceType.PLATFORM_ADMIN)
				.resourceId(saved.getId())
				.afterState(after)
				.build());
		return saved;
	}

	@Transactional
	public PlatformAdminGrant revoke(UUID userId, UUID revokedBy, String reason) {
		PlatformAdminGrant grant = grantRepository.findByUserId(userId)
				.filter(PlatformAdminGrant::isActive)
				.orElseThrow(() -> new BusinessException(ErrorCodes.ACCESS_DENIED, "User is not an active platform administrator"));
		grant.setActive(false);
		grant.setRevokedAt(Instant.now());
		grant.setRevokedBy(revokedBy);
		grant.setReason(reason);
		PlatformAdminGrant saved = grantRepository.save(grant);

		auditLogger.record(AuditEvent.builder()
				.actorUserId(revokedBy)
				.action(AuditAction.PLATFORM_ADMIN_REVOKED)
				.resourceType(AuditResourceType.PLATFORM_ADMIN)
				.resourceId(saved.getId())
				.beforeState(Map.of("userId", userId.toString(), "active", true))
				.afterState(Map.of("userId", userId.toString(), "active", false, "reason", reason == null ? "" : reason))
				.build());
		return saved;
	}

	@Transactional
	public PlatformAdminGrant bootstrapGrant(UUID userId, String reason) {
		if (grantRepository.existsByUserIdAndActiveTrue(userId)) {
			return grantRepository.findByUserId(userId).orElseThrow();
		}
		return grant(userId, null, reason == null ? "Bootstrap grant" : reason);
	}
}
