package com.finance.platform.finance.application.service;

import com.finance.platform.auth.api.UserFacade;
import com.finance.platform.auth.domain.model.Role;
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
import com.finance.platform.finance.application.dto.ClientResponse;
import com.finance.platform.finance.application.dto.CreateClientRequest;
import com.finance.platform.finance.application.dto.UpdateClientRequest;
import com.finance.platform.finance.domain.model.Client;
import com.finance.platform.finance.infrastructure.persistence.ClientJpaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Sort;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ClientService implements FirmClientLookup {

	private final ClientJpaRepository clientRepository;
	private final UserFacade userFacade;
	private final AuditLogger auditLogger;

	@Transactional(readOnly = true)
	public PageResponse<ClientResponse> listAccessibleClients(int page, int size) {
		SecurityUser user = SecurityUtils.requireCurrentUser();
		var pageable = PageRequests.of(page, size, Sort.by(Sort.Direction.ASC, "name"));
		Page<Client> clients;
		if (user.getRole() == Role.RoleCode.ADMIN) {
			clients = clientRepository.findByFirmIdAndDeletedAtIsNull(user.getFirmId(), pageable);
		} else {
			Set<UUID> accessibleIds = userFacade.getAccessibleClientIds(user.getId());
			if (accessibleIds.isEmpty()) {
				return new PageResponse<>(java.util.List.of(), pageable.getPageNumber(), pageable.getPageSize(), 0);
			}
			clients = clientRepository.findByFirmIdAndDeletedAtIsNullAndIdIn(user.getFirmId(), accessibleIds, pageable);
		}
		return new PageResponse<>(
				clients.stream().map(this::toResponse).toList(),
				clients.getNumber(),
				clients.getSize(),
				clients.getTotalElements()
		);
	}

	@Transactional(readOnly = true)
	public ClientResponse get(UUID clientId) {
		Client client = requireAccessibleClientEntity(clientId);
		return toResponse(client);
	}

	@Transactional
	public ClientResponse create(CreateClientRequest request) {
		SecurityUser currentUser = SecurityUtils.requireCurrentUser();
		assertAdmin(currentUser);

		String name = requireName(request.name());
		UUID firmId = currentUser.getFirmId();
		if (clientRepository.existsByFirmIdAndNameAndDeletedAtIsNull(firmId, name)) {
			throw new DuplicateResourceException("Client", "name", name);
		}

		Client client = Client.builder()
				.name(name)
				.businessRegNo(trimToNull(request.businessRegNo()))
				.contactEmail(trimToNull(request.contactEmail()))
				.active(true)
				.build();
		client.setFirmId(firmId);

		Client saved = clientRepository.save(client);
		auditLogger.record(AuditEvent.fromTenant()
				.firmId(saved.getFirmId())
				.action(AuditAction.CLIENT_CREATED)
				.resourceType(AuditResourceType.CLIENT)
				.resourceId(saved.getId())
				.clientId(saved.getId())
				.afterState(clientSnapshot(saved))
				.build());
		return toResponse(saved);
	}

	@Transactional
	public ClientResponse update(UUID clientId, UpdateClientRequest request) {
		SecurityUser currentUser = SecurityUtils.requireCurrentUser();
		assertAdmin(currentUser);
		Client client = findFirmClient(clientId, currentUser.getFirmId());
		Map<String, Object> before = clientSnapshot(client);

		String name = requireName(request.name());
		if (clientRepository.existsByFirmIdAndNameAndDeletedAtIsNullAndIdNot(currentUser.getFirmId(), name, clientId)) {
			throw new DuplicateResourceException("Client", "name", name);
		}

		client.setName(name);
		client.setBusinessRegNo(trimToNull(request.businessRegNo()));
		client.setContactEmail(trimToNull(request.contactEmail()));

		Client saved = clientRepository.save(client);
		auditLogger.record(AuditEvent.fromTenant()
				.firmId(saved.getFirmId())
				.action(AuditAction.CLIENT_UPDATED)
				.resourceType(AuditResourceType.CLIENT)
				.resourceId(saved.getId())
				.clientId(saved.getId())
				.beforeState(before)
				.afterState(clientSnapshot(saved))
				.build());
		return toResponse(saved);
	}

	@Transactional
	public ClientResponse setActive(UUID clientId, boolean active) {
		SecurityUser currentUser = SecurityUtils.requireCurrentUser();
		assertAdmin(currentUser);
		Client client = findFirmClient(clientId, currentUser.getFirmId());
		Map<String, Object> before = clientSnapshot(client);
		client.setActive(active);
		Client saved = clientRepository.save(client);
		auditLogger.record(AuditEvent.fromTenant()
				.firmId(saved.getFirmId())
				.action(active ? AuditAction.CLIENT_ACTIVATED : AuditAction.CLIENT_DEACTIVATED)
				.resourceType(AuditResourceType.CLIENT)
				.resourceId(saved.getId())
				.clientId(saved.getId())
				.beforeState(before)
				.afterState(clientSnapshot(saved))
				.build());
		return toResponse(saved);
	}

	@Override
	@Transactional(readOnly = true)
	public boolean existsByIdAndFirmId(UUID clientId, UUID firmId) {
		return clientRepository.findByIdAndFirmIdAndDeletedAtIsNull(clientId, firmId).isPresent();
	}

	@Override
	@Transactional(readOnly = true)
	public boolean existsActiveByIdAndFirmId(UUID clientId, UUID firmId) {
		return clientRepository.existsByIdAndFirmIdAndDeletedAtIsNullAndActiveTrue(clientId, firmId);
	}

	private Client requireAccessibleClientEntity(UUID clientId) {
		SecurityUser user = SecurityUtils.requireCurrentUser();
		Client client = findFirmClient(clientId, user.getFirmId());
		if (user.getRole() != Role.RoleCode.ADMIN && !userFacade.hasAccessToClient(user.getId(), clientId)) {
			throw new AccessDeniedException("Access denied to client");
		}
		return client;
	}

	private Client findFirmClient(UUID clientId, UUID firmId) {
		return clientRepository.findByIdAndFirmIdAndDeletedAtIsNull(clientId, firmId)
				.orElseThrow(() -> new ResourceNotFoundException("Client", clientId));
	}

	private void assertAdmin(SecurityUser currentUser) {
		if (currentUser.getRole() != Role.RoleCode.ADMIN) {
			throw new BusinessException(ErrorCodes.ACCESS_DENIED, "Only administrators can manage clients");
		}
	}

	private String requireName(String raw) {
		String name = raw == null ? "" : raw.trim();
		if (name.isBlank()) {
			throw new ValidationException("name", "Client name is required");
		}
		return name;
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

	private static Map<String, Object> clientSnapshot(Client client) {
		Map<String, Object> state = new LinkedHashMap<>();
		state.put("name", client.getName());
		state.put("businessRegNo", client.getBusinessRegNo());
		state.put("contactEmail", client.getContactEmail());
		state.put("active", client.isActive());
		return state;
	}

	private static String trimToNull(String value) {
		if (value == null) {
			return null;
		}
		String trimmed = value.trim();
		return trimmed.isEmpty() ? null : trimmed;
	}
}
