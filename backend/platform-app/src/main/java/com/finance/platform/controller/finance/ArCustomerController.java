package com.finance.platform.controller.finance;

import com.finance.platform.finance.application.dto.invoicing.ArCustomerRequest;
import com.finance.platform.finance.application.dto.invoicing.ArCustomerResponse;
import com.finance.platform.finance.application.service.invoicing.ArCustomerService;
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

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/ar/customers")
@RequiredArgsConstructor
public class ArCustomerController {

	private final ArCustomerService customerService;

	@GetMapping
	@PreAuthorize("hasAnyRole('ADMIN', 'ACCOUNTANT', 'AUDITOR')")
	public List<ArCustomerResponse> list(@RequestParam(defaultValue = "false") boolean includeInactive) {
		return customerService.list(includeInactive);
	}

	@GetMapping("/{customerId}")
	@PreAuthorize("hasAnyRole('ADMIN', 'ACCOUNTANT', 'AUDITOR')")
	public ArCustomerResponse get(@PathVariable UUID customerId) {
		return customerService.get(customerId);
	}

	@PostMapping
	@ResponseStatus(HttpStatus.CREATED)
	@PreAuthorize("hasAnyRole('ADMIN', 'ACCOUNTANT')")
	public ArCustomerResponse create(@Valid @RequestBody ArCustomerRequest request) {
		return customerService.create(request);
	}

	@PutMapping("/{customerId}")
	@PreAuthorize("hasAnyRole('ADMIN', 'ACCOUNTANT')")
	public ArCustomerResponse update(@PathVariable UUID customerId, @Valid @RequestBody ArCustomerRequest request) {
		return customerService.update(customerId, request);
	}

	@PostMapping("/{customerId}/deactivate")
	@PreAuthorize("hasRole('ADMIN')")
	public void deactivate(@PathVariable UUID customerId) {
		customerService.deactivate(customerId);
	}
}
