package com.finance.platform.controller.finance;

import com.finance.platform.core.dto.PageResponse;
import com.finance.platform.finance.application.dto.ExpenseRequest;
import com.finance.platform.finance.application.dto.ExpenseResponse;
import com.finance.platform.finance.application.service.ExpenseService;
import com.finance.platform.finance.domain.model.TransactionStatus;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
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
@RequestMapping("/api/v1/clients/{clientId}/expenses")
@RequiredArgsConstructor
public class ExpenseController {

	private final ExpenseService expenseService;

	@GetMapping
	@PreAuthorize("hasAnyRole('ADMIN', 'ACCOUNTANT', 'AUDITOR', 'BUSINESS_OWNER')")
	public PageResponse<ExpenseResponse> list(
			@PathVariable UUID clientId,
			@RequestParam(required = false) TransactionStatus status,
			@RequestParam(defaultValue = "0") int page,
			@RequestParam(defaultValue = "20") int size
	) {
		return expenseService.list(clientId, status, page, size);
	}

	@GetMapping("/{expenseId}")
	@PreAuthorize("hasAnyRole('ADMIN', 'ACCOUNTANT', 'AUDITOR', 'BUSINESS_OWNER')")
	public ExpenseResponse get(@PathVariable UUID clientId, @PathVariable UUID expenseId) {
		return expenseService.get(clientId, expenseId);
	}

	@PostMapping
	@ResponseStatus(HttpStatus.CREATED)
	@PreAuthorize("hasAnyRole('ADMIN', 'ACCOUNTANT', 'BUSINESS_OWNER')")
	public ExpenseResponse create(
			@PathVariable UUID clientId,
			@Valid @RequestBody ExpenseRequest request
	) {
		return expenseService.create(clientId, request);
	}

	@PutMapping("/{expenseId}")
	@PreAuthorize("hasAnyRole('ADMIN', 'ACCOUNTANT', 'BUSINESS_OWNER')")
	public ExpenseResponse update(
			@PathVariable UUID clientId,
			@PathVariable UUID expenseId,
			@Valid @RequestBody ExpenseRequest request
	) {
		return expenseService.update(clientId, expenseId, request);
	}

	@DeleteMapping("/{expenseId}")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	@PreAuthorize("hasAnyRole('ADMIN', 'ACCOUNTANT', 'BUSINESS_OWNER')")
	public void delete(@PathVariable UUID clientId, @PathVariable UUID expenseId) {
		expenseService.delete(clientId, expenseId);
	}

	@PostMapping("/{expenseId}/approve")
	@PreAuthorize("hasAnyRole('ADMIN', 'ACCOUNTANT')")
	public ExpenseResponse approve(@PathVariable UUID clientId, @PathVariable UUID expenseId) {
		return expenseService.approve(clientId, expenseId);
	}
}
