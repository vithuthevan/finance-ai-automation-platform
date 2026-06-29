package com.finance.platform.finance.application.service;

import com.finance.platform.auth.api.UserFacade;
import com.finance.platform.auth.domain.model.Role;
import com.finance.platform.auth.infrastructure.security.SecurityUser;
import com.finance.platform.auth.infrastructure.security.SecurityUtils;
import com.finance.platform.finance.application.dto.ClientResponse;
import com.finance.platform.finance.domain.model.Client;
import com.finance.platform.finance.infrastructure.persistence.ClientJpaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ClientService {

	private final ClientJpaRepository clientRepository;
	private final UserFacade userFacade;

	@Transactional(readOnly = true)
	public List<ClientResponse> listAccessibleClients() {
		SecurityUser user = SecurityUtils.requireCurrentUser();
		List<Client> clients = clientRepository.findByFirmIdAndDeletedAtIsNull(user.getFirmId());

		if (user.getRole() == Role.RoleCode.ADMIN) {
			return clients.stream().map(this::toResponse).toList();
		}

		Set<UUID> accessibleIds = userFacade.getAccessibleClientIds(user.getId());
		return clients.stream()
				.filter(client -> accessibleIds.contains(client.getId()))
				.map(this::toResponse)
				.toList();
	}

	private ClientResponse toResponse(Client client) {
		return new ClientResponse(
				client.getId(),
				client.getFirmId(),
				client.getName(),
				client.getBusinessRegNo(),
				client.getContactEmail(),
				client.isActive()
		);
	}
}
