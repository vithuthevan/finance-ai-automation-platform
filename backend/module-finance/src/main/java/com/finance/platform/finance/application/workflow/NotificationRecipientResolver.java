package com.finance.platform.finance.application.workflow;

import com.finance.platform.auth.domain.model.Role;
import com.finance.platform.auth.domain.model.User;
import com.finance.platform.auth.domain.model.UserClientAccess;
import com.finance.platform.auth.infrastructure.persistence.UserClientAccessJpaRepository;
import com.finance.platform.auth.infrastructure.persistence.UserJpaRepository;
import com.finance.platform.finance.domain.model.Client;
import com.finance.platform.finance.infrastructure.persistence.ClientJpaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class NotificationRecipientResolver {

	private final ClientJpaRepository clientRepository;
	private final UserClientAccessJpaRepository clientAccessRepository;
	private final UserJpaRepository userRepository;

	public Set<UUID> assignedAccountants(UUID firmId, UUID clientId) {
		Client client = clientRepository.findByIdAndFirmIdAndDeletedAtIsNull(clientId, firmId).orElse(null);
		if (client == null) {
			return Set.of();
		}
		if (client.getPrimaryAccountantUserId() != null) {
			return userRepository.findByIdAndFirmIdAndDeletedAtIsNull(client.getPrimaryAccountantUserId(), firmId)
					.filter(this::isOperationalAccountant)
					.map(user -> Set.of(user.getId()))
					.orElse(Set.of());
		}
		return clientAccessRepository.findByClientId(clientId).stream()
				.map(UserClientAccess::getUser)
				.filter(user -> user.getDeletedAt() == null && user.isActive())
				.filter(this::isOperationalAccountant)
				.map(User::getId)
				.collect(Collectors.toCollection(LinkedHashSet::new));
	}

	public Set<UUID> businessOwners(UUID clientId) {
		return clientAccessRepository.findByClientId(clientId).stream()
				.map(UserClientAccess::getUser)
				.filter(user -> user.getDeletedAt() == null && user.isActive())
				.filter(user -> user.getRole().getCode() == Role.RoleCode.BUSINESS_OWNER)
				.map(User::getId)
				.collect(Collectors.toCollection(LinkedHashSet::new));
	}

	public Set<UUID> uploadRecipients(UUID clientId) {
		return clientAccessRepository.findByClientId(clientId).stream()
				.filter(access -> access.getAccessType() != UserClientAccess.AccessType.READ_ONLY)
				.map(UserClientAccess::getUser)
				.filter(user -> user.getDeletedAt() == null && user.isActive())
				.filter(user -> user.getRole().getCode() == Role.RoleCode.BUSINESS_OWNER)
				.map(User::getId)
				.collect(Collectors.toCollection(LinkedHashSet::new));
	}

	public Set<UUID> auditors(UUID firmId, UUID clientId) {
		return clientAccessRepository.findByClientId(clientId).stream()
				.map(UserClientAccess::getUser)
				.filter(user -> user.getDeletedAt() == null && user.isActive())
				.filter(user -> user.getRole().getCode() == Role.RoleCode.AUDITOR)
				.map(User::getId)
				.collect(Collectors.toCollection(LinkedHashSet::new));
	}

	public Set<UUID> resolveDocumentRequestRecipients(UUID clientId, UUID assigneeUserId) {
		if (assigneeUserId != null) {
			return Set.of(assigneeUserId);
		}
		Set<UUID> owners = uploadRecipients(clientId);
		return owners.isEmpty() ? businessOwners(clientId) : owners;
	}

	private boolean isOperationalAccountant(User user) {
		Role.RoleCode role = user.getRole().getCode();
		return role == Role.RoleCode.ACCOUNTANT || role == Role.RoleCode.ADMIN;
	}
}
