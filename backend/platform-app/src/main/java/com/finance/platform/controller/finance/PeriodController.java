package com.finance.platform.controller.finance;

import com.finance.platform.finance.application.dto.PeriodResponse;
import com.finance.platform.finance.application.dto.VoidTransactionRequest;
import com.finance.platform.finance.application.service.PeriodCloseService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/clients/{clientId}/periods")
@RequiredArgsConstructor
public class PeriodController {

	private final PeriodCloseService periodCloseService;

	@GetMapping
	@PreAuthorize("hasAnyRole('ADMIN', 'ACCOUNTANT', 'AUDITOR')")
	public List<PeriodResponse> list(@PathVariable UUID clientId) {
		return periodCloseService.list(clientId);
	}

	@PostMapping
	@PreAuthorize("hasAnyRole('ADMIN', 'ACCOUNTANT')")
	public PeriodResponse getOrCreate(
			@PathVariable UUID clientId,
			@RequestParam int year,
			@RequestParam int month
	) {
		return periodCloseService.getOrCreate(clientId, year, month);
	}

	@PostMapping("/{periodId}/close")
	@PreAuthorize("hasAnyRole('ADMIN', 'ACCOUNTANT')")
	public PeriodResponse close(@PathVariable UUID clientId, @PathVariable UUID periodId) {
		return periodCloseService.close(clientId, periodId);
	}

	@PostMapping("/{periodId}/reopen")
	@PreAuthorize("hasAnyRole('ADMIN', 'ACCOUNTANT')")
	public PeriodResponse reopen(
			@PathVariable UUID clientId,
			@PathVariable UUID periodId,
			@Valid @RequestBody VoidTransactionRequest request
	) {
		return periodCloseService.reopen(clientId, periodId, request.reason());
	}
}
