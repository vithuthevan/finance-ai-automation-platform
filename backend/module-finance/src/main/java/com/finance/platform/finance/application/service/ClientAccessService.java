package com.finance.platform.finance.application.service;

import com.finance.platform.auth.api.UserFacade;
import com.finance.platform.auth.domain.model.Role;
import com.finance.platform.auth.domain.model.User;
import com.finance.platform.auth.domain.model.UserClientAccess;
import com.finance.platform.auth.infrastructure.persistence.UserJpaRepository;
import com.finance.platform.auth.infrastructure.security.SecurityUser;
import com.finance.platform.auth.infrastructure.security.SecurityUtils;
import com.finance.platform.core.exception.ErrorCodes;
import com.finance.platform.core.exception.ResourceNotFoundException;
import com.finance.platform.finance.domain.model.Client;
import com.finance.platform.finance.infrastructure.persistence.ClientJpaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;

import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ClientAccessService {

	private final ClientJpaRepository clientRepository;
	private final UserFacade userFacade;
	private final UserJpaRepository userRepository;

	public Client requireAccessibleClient(UUID clientId) {
		return requireReadAccess(clientId);
	}

	public Client requireReadAccess(UUID clientId) {
		Client client = requireFirmClient(clientId);
		assertHasClientMembership(clientId);
		return client;
	}

	public Client requireLedgerRead(UUID clientId) {
		Client client = requireReadAccess(clientId);
		if (effectiveAccessType(clientId) == UserClientAccess.AccessType.UPLOAD_ONLY) {
			throw new AccessDeniedException("Upload-only users cannot access ledger transactions");
		}
		return client;
	}

	public Client requireWriteAccess(UUID clientId) {
		Client client = requireReadAccess(clientId);
		assertClientActive(client);
		UserClientAccess.AccessType accessType = effectiveAccessType(clientId);
		if (accessType != UserClientAccess.AccessType.FULL) {
			throw new AccessDeniedException("Write access to this client is not permitted");
		}
		Role.RoleCode role = SecurityUtils.requireCurrentUser().getRole();
		if (role == Role.RoleCode.AUDITOR) {
			throw new AccessDeniedException("Auditors cannot modify bookkeeping records");
		}
		return client;
	}

	public Client requireUploadAccess(UUID clientId) {
		Client client = requireReadAccess(clientId);
		assertClientActive(client);
		UserClientAccess.AccessType accessType = effectiveAccessType(clientId);
		if (accessType == UserClientAccess.AccessType.READ_ONLY) {
			throw new AccessDeniedException("Upload access to this client is not permitted");
		}
		Role.RoleCode role = SecurityUtils.requireCurrentUser().getRole();
		if (role == Role.RoleCode.AUDITOR) {
			throw new AccessDeniedException("Auditors cannot upload documents");
		}
		return client;
	}

	public Client requireApproveAccess(UUID clientId) {
		Client client = requireWriteAccess(clientId);
		Role.RoleCode role = SecurityUtils.requireCurrentUser().getRole();
		if (role != Role.RoleCode.ADMIN && role != Role.RoleCode.ACCOUNTANT) {
			throw new AccessDeniedException("Only administrators and accountants can approve or void transactions");
		}
		return client;
	}

	public Client requireReportAccess(UUID clientId) {
		Client client = requireReadAccess(clientId);
		UserClientAccess.AccessType accessType = effectiveAccessType(clientId);
		if (accessType == UserClientAccess.AccessType.UPLOAD_ONLY) {
			throw new AccessDeniedException("Upload-only users cannot access financial reports");
		}
		return client;
	}

	public UserClientAccess.AccessType effectiveAccessType(UUID clientId) {
		SecurityUser currentUser = SecurityUtils.requireCurrentUser();
		if (currentUser.getRole() == Role.RoleCode.ADMIN) {
			return UserClientAccess.AccessType.FULL;
		}
		if (currentUser.getRole() == Role.RoleCode.AUDITOR) {
			return UserClientAccess.AccessType.READ_ONLY;
		}
		return userFacade.getAssignedAccessType(currentUser.getId(), clientId)
				.orElse(UserClientAccess.AccessType.READ_ONLY);
	}

	public Set<UUID> accessibleClientIds() {
		SecurityUser currentUser = SecurityUtils.requireCurrentUser();
		if (currentUser.getRole() == Role.RoleCode.ADMIN) {
			return Set.of();
		}
		return userFacade.getAccessibleClientIds(currentUser.getId());
	}

	public boolean isAdmin() {
		return SecurityUtils.requireCurrentUser().getRole() == Role.RoleCode.ADMIN;
	}

	public User requireCurrentUserEntity() {
		SecurityUser currentUser = SecurityUtils.requireCurrentUser();
		return userRepository.findById(currentUser.getId())
				.orElseThrow(() -> new ResourceNotFoundException("User", currentUser.getId()));
	}

	private Client requireFirmClient(UUID clientId) {
		SecurityUser currentUser = SecurityUtils.requireCurrentUser();
		return clientRepository.findByIdAndFirmIdAndDeletedAtIsNull(clientId, currentUser.getFirmId())
				.orElseThrow(() -> new ResourceNotFoundException("Client", clientId));
	}

	private void assertHasClientMembership(UUID clientId) {
		SecurityUser currentUser = SecurityUtils.requireCurrentUser();
		if (currentUser.getRole() == Role.RoleCode.ADMIN) {
			return;
		}
		if (!userFacade.hasAccessToClient(currentUser.getId(), clientId)) {
			throw new AccessDeniedException("Access denied to client");
		}
	}

	private void assertClientActive(Client client) {
		if (!client.isActive()) {
			throw new com.finance.platform.core.exception.BusinessException(
					ErrorCodes.CLIENT_INACTIVE,
					"Client is inactive"
			);
		}
	}
}
