package com.finance.platform.auth.api.impl;

import com.finance.platform.auth.api.UserFacade;
import com.finance.platform.auth.domain.model.Role;
import com.finance.platform.auth.domain.model.User;
import com.finance.platform.auth.domain.model.UserClientAccess;
import com.finance.platform.auth.infrastructure.persistence.UserClientAccessJpaRepository;
import com.finance.platform.auth.infrastructure.persistence.UserJpaRepository;
import com.finance.platform.core.exception.ResourceNotFoundException;
import com.finance.platform.core.security.ClientAccessType;
import com.finance.platform.core.security.ClientMembershipPort;
import com.finance.platform.core.security.FirmStaffPort;
import com.finance.platform.core.security.UserRole;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class UserFacadeImpl implements UserFacade, ClientMembershipPort, FirmStaffPort {

	private final UserJpaRepository userRepository;
	private final UserClientAccessJpaRepository clientAccessRepository;

	@Override
	public UserSummary getUser(UUID userId) {
		User user = userRepository.findById(userId)
				.orElseThrow(() -> new ResourceNotFoundException("User", userId));
		return toSummary(user);
	}

	@Override
	public Set<UUID> getAccessibleClientIds(UUID userId) {
		return accessibleClientIds(userId);
	}

	@Override
	public boolean hasAccessToClient(UUID userId, UUID clientId) {
		User user = userRepository.findById(userId)
				.orElseThrow(() -> new ResourceNotFoundException("User", userId));
		if (user.getRole().getCode() == Role.RoleCode.ADMIN) {
			return true;
		}
		return accessibleClientIds(userId).contains(clientId);
	}

	@Override
	public Optional<UserClientAccess.AccessType> getAssignedAccessType(UUID userId, UUID clientId) {
		return clientAccessRepository.findByUser_IdAndClientId(userId, clientId)
				.map(UserClientAccess::getAccessType);
	}

	@Override
	public Optional<ClientAccessType> assignedAccessType(UUID userId, UUID clientId) {
		return getAssignedAccessType(userId, clientId).map(type -> ClientAccessType.valueOf(type.name()));
	}

	@Override
	public Set<UUID> accessibleClientIds(UUID userId) {
		return clientAccessRepository.findByUser_Id(userId).stream()
				.map(UserClientAccess::getClientId)
				.collect(Collectors.toSet());
	}

	@Override
	public boolean isUploadOnlyWorkspace(UUID userId) {
		User user = userRepository.findById(userId)
				.orElseThrow(() -> new ResourceNotFoundException("User", userId));
		if (user.getRole().getCode() != Role.RoleCode.BUSINESS_OWNER) {
			return false;
		}
		var assignments = clientAccessRepository.findByUser_Id(userId);
		return !assignments.isEmpty()
				&& assignments.stream().allMatch(access -> access.getAccessType() == UserClientAccess.AccessType.UPLOAD_ONLY);
	}

	@Override
	public Optional<String> displayName(UUID userId) {
		return userRepository.findById(userId).map(User::getFullName);
	}

	@Override
	public Set<UUID> adminIds(UUID firmId) {
		return userRepository.findByFirmIdAndDeletedAtIsNull(firmId).stream()
				.filter(user -> user.getRole().getCode() == Role.RoleCode.ADMIN)
				.map(User::getId)
				.collect(Collectors.toSet());
	}

	@Override
	public boolean isActiveInFirmWithRoles(UUID userId, UUID firmId, Set<UserRole> roles) {
		return userRepository.findByIdAndFirmIdAndDeletedAtIsNull(userId, firmId)
				.filter(User::isActive)
				.map(user -> roles.contains(UserRole.valueOf(user.getRole().getCode().name())))
				.orElse(false);
	}

	@Override
	public Set<UUID> clientUserIdsByRole(UUID clientId, UserRole role, boolean excludeReadOnly) {
		return clientAccessRepository.findByClientId(clientId).stream()
				.filter(access -> !excludeReadOnly || access.getAccessType() != UserClientAccess.AccessType.READ_ONLY)
				.map(UserClientAccess::getUser)
				.filter(user -> user.getDeletedAt() == null && user.isActive())
				.filter(user -> user.getRole().getCode().name().equals(role.name()))
				.map(User::getId)
				.collect(Collectors.toSet());
	}

	@Override
	public List<StaffMember> activeStaff(UUID firmId) {
		return userRepository.findByFirmIdAndDeletedAtIsNull(firmId).stream()
				.filter(user -> {
					Role.RoleCode role = user.getRole().getCode();
					return role == Role.RoleCode.ACCOUNTANT || role == Role.RoleCode.ADMIN;
				})
				.map(user -> new StaffMember(
						user.getId(),
						user.getFullName(),
						UserRole.valueOf(user.getRole().getCode().name())))
				.toList();
	}

	private UserSummary toSummary(User user) {
		return new UserSummary(
				user.getId(),
				user.getFirmId(),
				user.getEmail(),
				user.getFullName(),
				UserRole.valueOf(user.getRole().getCode().name())
		);
	}
}
