package com.finance.platform.finance.application.service;

import com.finance.platform.auth.api.UserFacade;
import com.finance.platform.auth.domain.model.Role;
import com.finance.platform.auth.domain.model.User;
import com.finance.platform.auth.infrastructure.persistence.UserJpaRepository;
import com.finance.platform.auth.infrastructure.security.SecurityUser;
import com.finance.platform.auth.infrastructure.security.SecurityUtils;
import com.finance.platform.core.exception.BusinessException;
import com.finance.platform.core.exception.ResourceNotFoundException;
import com.finance.platform.finance.domain.model.Client;
import com.finance.platform.finance.infrastructure.persistence.ClientJpaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ClientAccessService {

	private final ClientJpaRepository clientRepository;
	private final UserFacade userFacade;
	private final UserJpaRepository userRepository;

	public Client requireAccessibleClient(UUID clientId) {
		SecurityUser currentUser = SecurityUtils.requireCurrentUser();
		Client client = clientRepository.findByIdAndFirmId(clientId, currentUser.getFirmId())
				.orElseThrow(() -> new ResourceNotFoundException("Client", clientId));

		if (currentUser.getRole() == Role.RoleCode.ADMIN) {
			return client;
		}
		if (!userFacade.hasAccessToClient(currentUser.getId(), clientId)) {
			throw new BusinessException("Access denied to client");
		}
		return client;
	}

	public User requireCurrentUserEntity() {
		SecurityUser currentUser = SecurityUtils.requireCurrentUser();
		return userRepository.findById(currentUser.getId())
				.orElseThrow(() -> new ResourceNotFoundException("User", currentUser.getId()));
	}
}
