package com.finance.platform.controller.finance;

import com.finance.platform.core.dto.PageResponse;
import com.finance.platform.finance.application.dto.DocumentResponse;
import com.finance.platform.finance.application.dto.IncomeRequest;
import com.finance.platform.finance.application.dto.IncomeResponse;
import com.finance.platform.finance.application.dto.LinkDocumentRequest;
import com.finance.platform.finance.application.dto.VoidTransactionRequest;
import com.finance.platform.finance.application.service.DocumentService;
import com.finance.platform.finance.application.service.IncomeService;
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

import java.time.LocalDate;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/clients/{clientId}/income")
@RequiredArgsConstructor
public class IncomeController {

	private final IncomeService incomeService;
	private final DocumentService documentService;

	@GetMapping
	@PreAuthorize("hasAnyRole('ADMIN', 'ACCOUNTANT', 'AUDITOR', 'BUSINESS_OWNER')")
	public PageResponse<IncomeResponse> list(
			@PathVariable UUID clientId,
			@RequestParam(required = false) TransactionStatus status,
			@RequestParam(required = false) UUID categoryId,
			@RequestParam(required = false) LocalDate from,
			@RequestParam(required = false) LocalDate to,
			@RequestParam(defaultValue = "0") int page,
			@RequestParam(defaultValue = "20") int size
	) {
		return incomeService.list(clientId, status, categoryId, from, to, page, size);
	}

	@GetMapping("/{incomeId}")
	@PreAuthorize("hasAnyRole('ADMIN', 'ACCOUNTANT', 'AUDITOR', 'BUSINESS_OWNER')")
	public IncomeResponse get(@PathVariable UUID clientId, @PathVariable UUID incomeId) {
		return incomeService.get(clientId, incomeId);
	}

	@PostMapping
	@ResponseStatus(HttpStatus.CREATED)
	@PreAuthorize("hasAnyRole('ADMIN', 'ACCOUNTANT', 'BUSINESS_OWNER')")
	public IncomeResponse create(
			@PathVariable UUID clientId,
			@Valid @RequestBody IncomeRequest request
	) {
		return incomeService.create(clientId, request);
	}

	@PutMapping("/{incomeId}")
	@PreAuthorize("hasAnyRole('ADMIN', 'ACCOUNTANT', 'BUSINESS_OWNER')")
	public IncomeResponse update(
			@PathVariable UUID clientId,
			@PathVariable UUID incomeId,
			@Valid @RequestBody IncomeRequest request
	) {
		return incomeService.update(clientId, incomeId, request);
	}

	@DeleteMapping("/{incomeId}")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	@PreAuthorize("hasAnyRole('ADMIN', 'ACCOUNTANT', 'BUSINESS_OWNER')")
	public void delete(@PathVariable UUID clientId, @PathVariable UUID incomeId) {
		incomeService.delete(clientId, incomeId);
	}

	@PostMapping("/{incomeId}/approve")
	@PreAuthorize("hasAnyRole('ADMIN', 'ACCOUNTANT')")
	public IncomeResponse approve(@PathVariable UUID clientId, @PathVariable UUID incomeId) {
		return incomeService.approve(clientId, incomeId);
	}

	@PostMapping("/{incomeId}/void")
	@PreAuthorize("hasAnyRole('ADMIN', 'ACCOUNTANT')")
	public IncomeResponse voidIncome(
			@PathVariable UUID clientId,
			@PathVariable UUID incomeId,
			@Valid @RequestBody VoidTransactionRequest request
	) {
		return incomeService.voidIncome(clientId, incomeId, request.reason());
	}

	@PostMapping("/{incomeId}/documents")
	@PreAuthorize("hasAnyRole('ADMIN', 'ACCOUNTANT')")
	public DocumentResponse linkDocument(
			@PathVariable UUID clientId,
			@PathVariable UUID incomeId,
			@Valid @RequestBody LinkDocumentRequest request
	) {
		return documentService.linkToIncome(clientId, incomeId, request.documentId());
	}

	@DeleteMapping("/{incomeId}/documents/{documentId}")
	@PreAuthorize("hasAnyRole('ADMIN', 'ACCOUNTANT')")
	public DocumentResponse unlinkDocument(
			@PathVariable UUID clientId,
			@PathVariable UUID incomeId,
			@PathVariable UUID documentId
	) {
		return documentService.unlink(clientId, documentId, null, incomeId);
	}
}
