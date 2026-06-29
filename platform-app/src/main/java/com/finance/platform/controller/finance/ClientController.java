package com.finance.platform.controller.finance;

import com.finance.platform.finance.application.dto.ClientResponse;
import com.finance.platform.finance.application.service.ClientService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/clients")
@RequiredArgsConstructor
public class ClientController {

	private final ClientService clientService;

	@GetMapping
	@PreAuthorize("isAuthenticated()")
	public List<ClientResponse> listClients() {
		return clientService.listAccessibleClients();
	}
}
