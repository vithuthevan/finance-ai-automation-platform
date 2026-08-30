package com.finance.platform.auth.application.service;

import com.finance.platform.auth.api.UserFacade;
import com.finance.platform.auth.application.dto.ChangePasswordRequest;
import com.finance.platform.auth.application.dto.ClientAccessAssignmentRequest;
import com.finance.platform.auth.application.dto.ClientAccessResponse;
import com.finance.platform.auth.application.dto.CreateUserRequest;
import com.finance.platform.auth.application.dto.ReplaceClientAccessRequest;
import com.finance.platform.auth.application.dto.UpdateUserRequest;
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
import com.finance.platform.core.exception.ErrorCodes;
import com.finance.platform.core.exception.ResourceNotFoundException;
import com.finance.platform.core.dto.PageRequests;
import com.finance.platform.core.dto.PageResponse;
import com.finance.platform.core.exception.ValidationException;
import com.finance.platform.core.security.FirmClientLookup;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Sort;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
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
	private final FirmClientLookup firmClientLookup;
	private final PasswordEncoder passwordEncoder;
	private final AuditLogger auditLogger;
	private final SessionService sessionService;

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
				userFacade.getAccessibleClientIds(currentUser.getId()),
				userFacade.isUploadOnlyWorkspace(currentUser.getId())
		);
	}

	@Transactional(readOnly = true)
	public PageResponse<UserResponse> listFirmUsers(int page, int size) {
		SecurityUser currentUser = SecurityUtils.requireCurrentUser();
		assertAdmin(currentUser);
		Page<User> users = userRepository.findByFirmIdAndDeletedAtIsNull(
				currentUser.getFirmId(),
				PageRequests.of(page, size, Sort.by(Sort.Direction.ASC, "fullName")));
		Map<UUID, List<UserClientAccess>> accessByUser = new LinkedHashMap<>();
		List<UUID> userIds = users.getContent().stream().map(User::getId).toList();
		if (!userIds.isEmpty()) {
			for (UserClientAccess access : clientAccessRepository.findByUser_IdIn(userIds)) {
				accessByUser.computeIfAbsent(access.getUser().getId(), ignored -> new ArrayList<>()).add(access);
			}
		}
		return new PageResponse<>(
				users.stream()
						.map(user -> toResponse(user, accessByUser.getOrDefault(user.getId(), List.of())))
						.toList(),
				users.getNumber(),
				users.getSize(),
				users.getTotalElements()
		);
	}

	@Transactional(readOnly = true)
	public UserResponse getUser(UUID userId) {
		SecurityUser currentUser = SecurityUtils.requireCurrentUser();
		User user = findFirmUser(userId, currentUser.getFirmId());
		assertSelfOrAdmin(currentUser, userId);
		return toResponse(user, clientAccessRepository.findByUser_Id(user.getId()));
	}

	@Transactional
	public UserResponse createUser(CreateUserRequest request) {
		SecurityUser currentUser = SecurityUtils.requireCurrentUser();
		assertAdmin(currentUser);

		if (userRepository.findByEmailAndDeletedAtIsNull(request.email()).isPresent()) {
			throw new DuplicateResourceException("User", "email", request.email());
		}

		Role role = roleRepository.findByCode(parseRole(request.role()))
				.orElseThrow(() -> new ValidationException("role", "Invalid role: " + request.role()));

		List<ResolvedAssignment> assignments = resolveAssignments(request, role.getCode());
		UUID firmId = currentUser.getFirmId();
		for (ResolvedAssignment assignment : assignments) {
			requireActiveClientInCurrentFirm(assignment.clientId(), firmId);
		}

		User user = User.builder()
				.role(role)
				.email(request.email())
				.passwordHash(passwordEncoder.encode(request.password()))
				.fullName(request.fullName())
				.active(true)
				.build();
		user.setFirmId(firmId);
		user = userRepository.save(user);

		List<UserClientAccess> savedAccess = new ArrayList<>();
		for (ResolvedAssignment assignment : assignments) {
			UserClientAccess access = UserClientAccess.builder()
					.user(user)
					.clientId(assignment.clientId())
					.accessType(assignment.accessType())
					.build();
			savedAccess.add(clientAccessRepository.save(access));
		}

		Map<String, Object> after = userSnapshot(user, savedAccess);
		auditLogger.record(AuditEvent.fromTenant()
				.firmId(user.getFirmId())
				.action(AuditAction.USER_CREATED)
				.resourceType(AuditResourceType.USER)
				.resourceId(user.getId())
				.afterState(after)
				.build());

		return toResponse(user, savedAccess);
	}

	@Transactional
	public UserResponse updateUser(UUID userId, UpdateUserRequest request) {
		SecurityUser currentUser = SecurityUtils.requireCurrentUser();
		assertAdmin(currentUser);
		User user = findFirmUser(userId, currentUser.getFirmId());
		Map<String, Object> before = userSnapshot(user, clientAccessRepository.findByUser_Id(user.getId()));

		if (request.fullName() != null && !request.fullName().isBlank()) {
			user.setFullName(request.fullName().trim());
		}
		if (request.phone() != null) {
			String phone = request.phone().trim();
			user.setPhone(phone.isEmpty() ? null : phone);
		}
		if (request.role() != null && !request.role().isBlank()) {
			Role role = roleRepository.findByCode(parseRole(request.role()))
					.orElseThrow(() -> new ValidationException("role", "Invalid role: " + request.role()));
			user.setRole(role);
		}

		User saved = userRepository.save(user);
		List<UserClientAccess> access = clientAccessRepository.findByUser_Id(saved.getId());
		if (saved.getRole().getCode() == Role.RoleCode.AUDITOR) {
			for (UserClientAccess item : access) {
				item.setAccessType(UserClientAccess.AccessType.READ_ONLY);
				clientAccessRepository.save(item);
			}
		}
		auditLogger.record(AuditEvent.fromTenant()
				.firmId(saved.getFirmId())
				.action(AuditAction.USER_UPDATED)
				.resourceType(AuditResourceType.USER)
				.resourceId(saved.getId())
				.beforeState(before)
				.afterState(userSnapshot(saved, access))
				.build());
		return toResponse(saved, access);
	}

	@Transactional
	public UserResponse setActive(UUID userId, boolean active) {
		SecurityUser currentUser = SecurityUtils.requireCurrentUser();
		assertAdmin(currentUser);
		if (currentUser.getId().equals(userId)) {
			throw new BusinessException("Administrators cannot change their own active status");
		}
		User user = findFirmUser(userId, currentUser.getFirmId());
		Map<String, Object> before = userSnapshot(user, clientAccessRepository.findByUser_Id(user.getId()));
		user.setActive(active);
		if (!active) {
			user.setDeletedAt(null);
			sessionService.revokeAllForUser(user.getId());
		}
		User saved = userRepository.save(user);
		List<UserClientAccess> access = clientAccessRepository.findByUser_Id(saved.getId());
		auditLogger.record(AuditEvent.fromTenant()
				.firmId(saved.getFirmId())
				.action(active ? AuditAction.USER_ACTIVATED : AuditAction.USER_DEACTIVATED)
				.resourceType(AuditResourceType.USER)
				.resourceId(saved.getId())
				.beforeState(before)
				.afterState(userSnapshot(saved, access))
				.build());
		return toResponse(saved, access);
	}

	@Transactional
	public List<ClientAccessResponse> replaceClientAccess(UUID userId, ReplaceClientAccessRequest request) {
		SecurityUser currentUser = SecurityUtils.requireCurrentUser();
		assertAdmin(currentUser);
		User user = findFirmUser(userId, currentUser.getFirmId());

		List<ResolvedAssignment> assignments = new ArrayList<>();
		Map<UUID, UserClientAccess.AccessType> unique = new LinkedHashMap<>();
		if (request.assignments() != null) {
			for (ClientAccessAssignmentRequest assignment : request.assignments()) {
				if (assignment == null || assignment.clientId() == null) {
					continue;
				}
				unique.put(assignment.clientId(), normalizeAccessType(user.getRole().getCode(), parseAccessType(assignment.accessType())));
			}
		}
		for (Map.Entry<UUID, UserClientAccess.AccessType> entry : unique.entrySet()) {
			requireActiveClientInCurrentFirm(entry.getKey(), currentUser.getFirmId());
			assignments.add(new ResolvedAssignment(entry.getKey(), entry.getValue()));
		}

		clientAccessRepository.deleteByUser_Id(user.getId());
		clientAccessRepository.flush();

		List<UserClientAccess> savedAccess = new ArrayList<>();
		for (ResolvedAssignment assignment : assignments) {
			savedAccess.add(clientAccessRepository.save(UserClientAccess.builder()
					.user(user)
					.clientId(assignment.clientId())
					.accessType(assignment.accessType())
					.build()));
		}

		auditLogger.record(AuditEvent.fromTenant()
				.firmId(user.getFirmId())
				.action(AuditAction.USER_UPDATED)
				.resourceType(AuditResourceType.USER)
				.resourceId(user.getId())
				.afterState(userSnapshot(user, savedAccess))
				.build());
		return savedAccess.stream().map(this::toAccessResponse).toList();
	}

	@Transactional
	public void changePassword(ChangePasswordRequest request) {
		SecurityUser currentUser = SecurityUtils.requireCurrentUser();
		User user = findFirmUser(currentUser.getId(), currentUser.getFirmId());
		if (!passwordEncoder.matches(request.currentPassword(), user.getPasswordHash())) {
			throw new ValidationException("currentPassword", "Current password is incorrect");
		}
		if (request.currentPassword().equals(request.newPassword())) {
			throw new ValidationException("newPassword", "New password must be different from the current password");
		}
		user.setPasswordHash(passwordEncoder.encode(request.newPassword()));
		userRepository.save(user);
		auditLogger.record(AuditEvent.fromTenant()
				.firmId(user.getFirmId())
				.action(AuditAction.USER_PASSWORD_CHANGED)
				.resourceType(AuditResourceType.USER)
				.resourceId(user.getId())
				.build());
	}

	private List<ResolvedAssignment> resolveAssignments(CreateUserRequest request, Role.RoleCode role) {
		Map<UUID, UserClientAccess.AccessType> unique = new LinkedHashMap<>();
		if (request.clientAccess() != null) {
			for (ClientAccessAssignmentRequest assignment : request.clientAccess()) {
				if (assignment == null || assignment.clientId() == null) {
					continue;
				}
				unique.put(assignment.clientId(), normalizeAccessType(role, parseAccessType(assignment.accessType())));
			}
		}
		if (request.clientIds() != null) {
			for (UUID clientId : request.clientIds()) {
				if (clientId == null) {
					continue;
				}
				unique.putIfAbsent(clientId, normalizeAccessType(role, UserClientAccess.AccessType.FULL));
			}
		}
		List<ResolvedAssignment> assignments = new ArrayList<>();
		for (Map.Entry<UUID, UserClientAccess.AccessType> entry : unique.entrySet()) {
			assignments.add(new ResolvedAssignment(entry.getKey(), entry.getValue()));
		}
		return assignments;
	}

	private void requireActiveClientInCurrentFirm(UUID clientId, UUID firmId) {
		if (clientId == null || !firmClientLookup.existsActiveByIdAndFirmId(clientId, firmId)) {
			throw new ResourceNotFoundException("Client", clientId);
		}
	}

	private User findFirmUser(UUID userId, UUID firmId) {
		return userRepository.findByIdAndFirmIdAndDeletedAtIsNull(userId, firmId)
				.orElseThrow(() -> new ResourceNotFoundException("User", userId));
	}

	private void assertSelfOrAdmin(SecurityUser currentUser, UUID targetUserId) {
		if (currentUser.getRole() != Role.RoleCode.ADMIN && !currentUser.getId().equals(targetUserId)) {
			throw new AccessDeniedException("Access denied");
		}
	}

	private void assertAdmin(SecurityUser currentUser) {
		if (currentUser.getRole() != Role.RoleCode.ADMIN) {
			throw new BusinessException(ErrorCodes.ACCESS_DENIED, "Only administrators can manage users");
		}
	}

	private Role.RoleCode parseRole(String role) {
		try {
			return Role.RoleCode.valueOf(role.toUpperCase());
		} catch (IllegalArgumentException ex) {
			throw new ValidationException("role", "Invalid role: " + role);
		}
	}

	private UserClientAccess.AccessType normalizeAccessType(Role.RoleCode role, UserClientAccess.AccessType requested) {
		if (role == Role.RoleCode.AUDITOR) {
			return UserClientAccess.AccessType.READ_ONLY;
		}
		if (requested == UserClientAccess.AccessType.UPLOAD_ONLY && role != Role.RoleCode.BUSINESS_OWNER) {
			throw new ValidationException("accessType", "UPLOAD_ONLY is only valid for BUSINESS_OWNER");
		}
		return requested;
	}

	private UserClientAccess.AccessType parseAccessType(String accessType) {
		if (accessType == null || accessType.isBlank()) {
			return UserClientAccess.AccessType.FULL;
		}
		try {
			return UserClientAccess.AccessType.valueOf(accessType.trim().toUpperCase());
		} catch (IllegalArgumentException ex) {
			throw new ValidationException("accessType", "Invalid access type: " + accessType);
		}
	}

	private UserResponse toResponse(User user, List<UserClientAccess> access) {
		return new UserResponse(
				user.getId(),
				user.getFirmId(),
				user.getEmail(),
				user.getFullName(),
				user.getRole().getCode().name(),
				user.isActive(),
				access.stream().map(this::toAccessResponse).toList()
		);
	}

	private ClientAccessResponse toAccessResponse(UserClientAccess access) {
		return new ClientAccessResponse(
				access.getClientId(),
				access.getAccessType() != null ? access.getAccessType().name() : UserClientAccess.AccessType.FULL.name()
		);
	}

	private Map<String, Object> userSnapshot(User user, List<UserClientAccess> access) {
		Map<String, Object> after = new LinkedHashMap<>();
		after.put("email", user.getEmail());
		after.put("fullName", user.getFullName());
		after.put("role", user.getRole().getCode().name());
		after.put("active", user.isActive());
		after.put("clientIds", access.stream().map(UserClientAccess::getClientId).toList());
		after.put("accessTypes", access.stream()
				.map(item -> item.getAccessType() != null ? item.getAccessType().name() : null)
				.toList());
		return after;
	}

	private record ResolvedAssignment(UUID clientId, UserClientAccess.AccessType accessType) {
	}
}
