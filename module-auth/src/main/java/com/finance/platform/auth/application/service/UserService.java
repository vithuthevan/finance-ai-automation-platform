package com.finance.platform.auth.application.service;

import com.finance.platform.auth.api.UserFacade;
import com.finance.platform.auth.application.dto.CreateUserRequest;
import com.finance.platform.auth.application.dto.UserProfileResponse;
import com.finance.platform.auth.application.dto.UserResponse;
import com.finance.platform.auth.domain.model.Role;
import com.finance.platform.auth.domain.model.User;
import com.finance.platform.auth.domain.model.UserClientAccess;
import com.finance.platform.auth.infrastructure.persistence.RoleJpaRepository;
import com.finance.platform.auth.infrastructure.persistence.UserClientAccessJpaRepository;
import com.finance.platform.auth.infrastructure.persistence.UserJpaRepository;
import com.finance.platform.auth.infrastructure.security.SecurityUser;
import com.finance.platform.auth.infrastructure.security.SecurityUtils;
import com.finance.platform.core.audit.AuditAction;
import com.finance.platform.core.audit.AuditEvent;
import com.finance.platform.core.audit.AuditLogger;
import com.finance.platform.core.audit.AuditResourceType;
import com.finance.platform.core.exception.BusinessException;
import com.finance.platform.core.exception.DuplicateResourceException;
import com.finance.platform.core.exception.ResourceNotFoundException;
import com.finance.platform.core.exception.ValidationException;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class UserService {

	private final UserJpaRepository userRepository;
	private final RoleJpaRepository roleRepository;
	private final UserClientAccessJpaRepository clientAccessRepository;
	private final UserFacade userFacade;
	private final PasswordEncoder passwordEncoder;
	private final AuditLogger auditLogger;

	@Transactional(readOnly = true)
	public UserProfileResponse getProfile() {
		SecurityUser currentUser = SecurityUtils.requireCurrentUser();
		UserFacade.UserSummary summary = userFacade.getUser(currentUser.getId());
		return new UserProfileResponse(
				summary.id(),
				summary.firmId(),
				summary.email(),
				summary.fullName(),
				summary.role().name(),
				userFacade.getAccessibleClientIds(currentUser.getId())
		);
	}

	@Transactional(readOnly = true)
	public List<UserResponse> listFirmUsers() {
		SecurityUser currentUser = SecurityUtils.requireCurrentUser();
		return userRepository.findByFirmIdAndDeletedAtIsNull(currentUser.getFirmId()).stream()
				.map(this::toResponse)
				.toList();
	}

	@Transactional(readOnly = true)
	public UserResponse getUser(UUID userId) {
		SecurityUser currentUser = SecurityUtils.requireCurrentUser();
		User user = findFirmUser(userId, currentUser.getFirmId());
		assertSelfOrAdmin(currentUser, userId);
		return toResponse(user);
	}

	@Transactional
	public UserResponse createUser(CreateUserRequest request) {
		SecurityUser currentUser = SecurityUtils.requireCurrentUser();
		if (currentUser.getRole() != Role.RoleCode.ADMIN) {
			throw new BusinessException("Only administrators can create users");
		}
		if (userRepository.findByEmailAndDeletedAtIsNull(request.email()).isPresent()) {
			throw new DuplicateResourceException("User", "email", request.email());
		}

		Role role = roleRepository.findByCode(parseRole(request.role()))
				.orElseThrow(() -> new ValidationException("role", "Invalid role: " + request.role()));

		User user = User.builder()
				.role(role)
				.email(request.email())
				.passwordHash(passwordEncoder.encode(request.password()))
				.fullName(request.fullName())
				.active(true)
				.build();
		user.setFirmId(currentUser.getFirmId());
		user = userRepository.save(user);

		if (request.clientIds() != null) {
			for (UUID clientId : request.clientIds()) {
				UserClientAccess access = UserClientAccess.builder()
						.user(user)
						.clientId(clientId)
						.build();
				clientAccessRepository.save(access);
			}
		}

		Map<String, Object> after = new LinkedHashMap<>();
		after.put("email", user.getEmail());
		after.put("fullName", user.getFullName());
		after.put("role", user.getRole().getCode().name());
		after.put("active", user.isActive());
		after.put("clientIds", request.clientIds());

		auditLogger.record(AuditEvent.fromTenant()
				.firmId(user.getFirmId())
				.action(AuditAction.USER_CREATED)
				.resourceType(AuditResourceType.USER)
				.resourceId(user.getId())
				.afterState(after)
				.build());

		return toResponse(user);
	}

	private User findFirmUser(UUID userId, UUID firmId) {
		return userRepository.findByIdAndFirmIdAndDeletedAtIsNull(userId, firmId)
				.orElseThrow(() -> new ResourceNotFoundException("User", userId));
	}

	private void assertSelfOrAdmin(SecurityUser currentUser, UUID targetUserId) {
		if (currentUser.getRole() != Role.RoleCode.ADMIN && !currentUser.getId().equals(targetUserId)) {
			throw new BusinessException("Access denied");
		}
	}

	private Role.RoleCode parseRole(String role) {
		try {
			return Role.RoleCode.valueOf(role.toUpperCase());
		} catch (IllegalArgumentException ex) {
			throw new ValidationException("role", "Invalid role: " + role);
		}
	}

	private UserResponse toResponse(User user) {
		return new UserResponse(
				user.getId(),
				user.getFirmId(),
				user.getEmail(),
				user.getFullName(),
				user.getRole().getCode().name(),
				user.isActive()
		);
	}
}
