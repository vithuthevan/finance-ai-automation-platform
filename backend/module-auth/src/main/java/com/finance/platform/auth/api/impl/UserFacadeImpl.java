package com.finance.platform.auth.api.impl;

import com.finance.platform.auth.api.UserFacade;
import com.finance.platform.auth.domain.model.Role;
import com.finance.platform.auth.domain.model.User;
import com.finance.platform.auth.domain.model.UserClientAccess;
import com.finance.platform.auth.infrastructure.persistence.UserClientAccessJpaRepository;
import com.finance.platform.auth.infrastructure.persistence.UserJpaRepository;
import com.finance.platform.core.exception.ResourceNotFoundException;
import com.finance.platform.core.security.UserRole;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class UserFacadeImpl implements UserFacade {

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
		return clientAccessRepository.findByUser_Id(userId).stream()
				.map(UserClientAccess::getClientId)
				.collect(Collectors.toSet());
	}

	@Override
	public boolean hasAccessToClient(UUID userId, UUID clientId) {
		User user = userRepository.findById(userId)
				.orElseThrow(() -> new ResourceNotFoundException("User", userId));
		if (user.getRole().getCode() == Role.RoleCode.ADMIN) {
			return true;
		}
		return getAccessibleClientIds(userId).contains(clientId);
	}

	@Override
	public Optional<UserClientAccess.AccessType> getAssignedAccessType(UUID userId, UUID clientId) {
		return clientAccessRepository.findByUser_IdAndClientId(userId, clientId)
				.map(UserClientAccess::getAccessType);
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
