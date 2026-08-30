package com.finance.platform.auth.application.service;

import com.finance.platform.auth.domain.model.Role;
import com.finance.platform.auth.domain.model.User;
import com.finance.platform.auth.infrastructure.persistence.RoleJpaRepository;
import com.finance.platform.auth.infrastructure.persistence.UserJpaRepository;
import com.finance.platform.core.audit.AuditAction;
import com.finance.platform.core.audit.AuditEvent;
import com.finance.platform.core.audit.AuditLogger;
import com.finance.platform.core.audit.AuditResourceType;
import com.finance.platform.core.exception.BusinessException;
import com.finance.platform.core.exception.DuplicateResourceException;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class UserRegistrationService {

	private final UserJpaRepository userRepository;
	private final RoleJpaRepository roleRepository;
	private final PasswordEncoder passwordEncoder;
	private final AuditLogger auditLogger;

	@Transactional
	public User registerAdmin(UUID firmId, String email, String rawPassword, String fullName) {
		if (userRepository.findByEmailAndDeletedAtIsNull(email).isPresent()) {
			throw new DuplicateResourceException("User", "email", email);
		}

		Role adminRole = roleRepository.findByCode(Role.RoleCode.ADMIN)
				.orElseThrow(() -> new BusinessException("Admin role not configured"));

		User user = User.builder()
				.role(adminRole)
				.email(email)
				.passwordHash(passwordEncoder.encode(rawPassword))
				.fullName(fullName)
				.active(true)
				.build();
		user.setFirmId(firmId);

		User saved = userRepository.save(user);

		Map<String, Object> after = new LinkedHashMap<>();
		after.put("email", saved.getEmail());
		after.put("fullName", saved.getFullName());
		after.put("role", saved.getRole().getCode().name());
		after.put("active", saved.isActive());
		after.put("registration", true);

		auditLogger.record(AuditEvent.builder()
				.firmId(firmId)
				.actorUserId(saved.getId())
				.actorRole(Role.RoleCode.ADMIN.name())
				.action(AuditAction.USER_CREATED)
				.resourceType(AuditResourceType.USER)
				.resourceId(saved.getId())
				.afterState(after)
				.build());

		return saved;
	}
}
