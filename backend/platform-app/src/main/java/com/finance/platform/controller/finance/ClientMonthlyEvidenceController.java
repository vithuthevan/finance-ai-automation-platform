package com.finance.platform.controller.finance;

import com.finance.platform.finance.application.dto.ClientMonthlyEvidenceItemResponse;
import com.finance.platform.finance.application.dto.GenerateMonthlyEvidenceRequestsRequest;
import com.finance.platform.finance.application.dto.GenerateMonthlyEvidenceRequestsResponse;
import com.finance.platform.finance.application.dto.UpsertClientMonthlyEvidenceItemRequest;
import com.finance.platform.finance.application.service.ClientMonthlyEvidenceService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
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
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/clients/{clientId}/monthly-evidence")
@RequiredArgsConstructor
@Tag(name = "Monthly evidence checklist", description = "Configurable expected month-end evidence per client.")
public class ClientMonthlyEvidenceController {

	private final ClientMonthlyEvidenceService evidenceService;

	@GetMapping
	@PreAuthorize("hasAnyRole('ADMIN', 'ACCOUNTANT', 'AUDITOR')")
	@Operation(summary = "List active monthly evidence checklist items")
	public List<ClientMonthlyEvidenceItemResponse> list(@PathVariable UUID clientId) {
		return evidenceService.list(clientId);
	}

	@PostMapping
	@ResponseStatus(HttpStatus.CREATED)
	@PreAuthorize("hasAnyRole('ADMIN', 'ACCOUNTANT')")
	@Operation(summary = "Add checklist item")
	public ClientMonthlyEvidenceItemResponse create(
			@PathVariable UUID clientId,
			@Valid @RequestBody UpsertClientMonthlyEvidenceItemRequest request
	) {
		return evidenceService.create(clientId, request);
	}

	@PutMapping("/{itemId}")
	@PreAuthorize("hasAnyRole('ADMIN', 'ACCOUNTANT')")
	@Operation(summary = "Update checklist item")
	public ClientMonthlyEvidenceItemResponse update(
			@PathVariable UUID clientId,
			@PathVariable UUID itemId,
			@Valid @RequestBody UpsertClientMonthlyEvidenceItemRequest request
	) {
		return evidenceService.update(clientId, itemId, request);
	}

	@PostMapping("/generate-requests")
	@PreAuthorize("hasAnyRole('ADMIN', 'ACCOUNTANT')")
	@Operation(summary = "Create document requests from checklist for a calendar month (does not send email automatically)")
	public GenerateMonthlyEvidenceRequestsResponse generateRequests(
			@PathVariable UUID clientId,
			@Valid @RequestBody GenerateMonthlyEvidenceRequestsRequest request
	) {
		return evidenceService.generateRequests(clientId, request);
	}
}
