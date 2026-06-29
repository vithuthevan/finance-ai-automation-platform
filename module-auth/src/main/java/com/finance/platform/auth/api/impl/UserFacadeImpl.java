package com.finance.platform.auth.api.impl;

import com.finance.platform.auth.api.UserFacade;
import com.finance.platform.auth.domain.model.Role;
import com.finance.platform.auth.domain.model.User;
import com.finance.platform.auth.infrastructure.persistence.UserJpaRepository;
import com.finance.platform.core.exception.ResourceNotFoundException;
import com.finance.platform.core.security.UserRole;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collections;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class UserFacadeImpl implements UserFacade {

	private final UserJpaRepository userRepository;

	@Override
	public UserSummary getUser(UUID userId) {
		User user = userRepository.findById(userId)
				.orElseThrow(() -> new ResourceNotFoundException("User", userId));
		return toSummary(user);
	}

	@Override
	public Set<UUID> getAccessibleClientIds(UUID userId) {
		// TODO: resolve from user_client_access table
		return Collections.emptySet();
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
