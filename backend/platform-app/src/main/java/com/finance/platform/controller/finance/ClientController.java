package com.finance.platform.controller.finance;

import com.finance.platform.finance.application.dto.ClientResponse;
import com.finance.platform.finance.application.dto.CreateClientRequest;
import com.finance.platform.finance.application.dto.UpdateClientRequest;
import com.finance.platform.core.dto.PageResponse;
import com.finance.platform.finance.application.service.ClientService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/clients")
@RequiredArgsConstructor
public class ClientController {

	private final ClientService clientService;

	@GetMapping
	@PreAuthorize("isAuthenticated()")
	public PageResponse<ClientResponse> listClients(
			@RequestParam(defaultValue = "0") int page,
			@RequestParam(defaultValue = "50") int size
	) {
		return clientService.listAccessibleClients(page, size);
	}

	@GetMapping("/{clientId}")
	@PreAuthorize("isAuthenticated()")
	public ClientResponse getClient(@PathVariable UUID clientId) {
		return clientService.get(clientId);
	}

	@PostMapping
	@ResponseStatus(HttpStatus.CREATED)
	@PreAuthorize("hasRole('ADMIN')")
	public ClientResponse createClient(@Valid @RequestBody CreateClientRequest request) {
		return clientService.create(request);
	}

	@PutMapping("/{clientId}")
	@PreAuthorize("hasRole('ADMIN')")
	public ClientResponse updateClient(
			@PathVariable UUID clientId,
			@Valid @RequestBody UpdateClientRequest request
	) {
		return clientService.update(clientId, request);
	}

	@PutMapping("/{clientId}/primary-accountant")
	@PreAuthorize("hasRole('ADMIN')")
	public ClientResponse assignPrimaryAccountant(
			@PathVariable UUID clientId,
			@RequestBody PrimaryAccountantRequest request
	) {
		return clientService.assignPrimaryAccountant(clientId, request.accountantUserId());
	}

	@PostMapping("/{clientId}/activate")
	@PreAuthorize("hasRole('ADMIN')")
	public ClientResponse activateClient(@PathVariable UUID clientId) {
		return clientService.setActive(clientId, true);
	}

	@PostMapping("/{clientId}/deactivate")
	@PreAuthorize("hasRole('ADMIN')")
	public ClientResponse deactivateClient(@PathVariable UUID clientId) {
		return clientService.setActive(clientId, false);
	}

	public record PrimaryAccountantRequest(UUID accountantUserId) {
	}
}
