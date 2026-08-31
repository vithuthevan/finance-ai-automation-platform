package com.finance.platform.controller.finance;

import com.finance.platform.finance.application.dto.ClosePeriodRequest;
import com.finance.platform.finance.application.dto.PeriodReadinessResponse;
import com.finance.platform.finance.application.dto.PeriodResponse;
import com.finance.platform.finance.application.dto.ReopenPeriodRequest;
import com.finance.platform.finance.application.service.PeriodCloseService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
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
@Tag(name = "Close", description = "Accounting periods, close readiness, review, close, and reopen. ADMIN/ACCOUNTANT+FULL close assigned clients. Reopen is ADMIN only. AUDITOR is read-only. BUSINESS_OWNER and UPLOAD_ONLY cannot manage close.")
public class PeriodController {

	private final PeriodCloseService periodCloseService;

	@GetMapping
	@PreAuthorize("hasAnyRole('ADMIN', 'ACCOUNTANT', 'AUDITOR')")
	@Operation(summary = "List accounting periods for a client")
	public List<PeriodResponse> list(@PathVariable UUID clientId) {
		return periodCloseService.list(clientId);
	}

	@PostMapping
	@PreAuthorize("hasAnyRole('ADMIN', 'ACCOUNTANT')")
	@Operation(summary = "Create or return the monthly accounting period (calendar month bounds)")
	public PeriodResponse getOrCreate(
			@PathVariable UUID clientId,
			@RequestParam int year,
			@RequestParam int month
	) {
		return periodCloseService.getOrCreate(clientId, year, month);
	}

	@GetMapping("/{periodId}")
	@PreAuthorize("hasAnyRole('ADMIN', 'ACCOUNTANT', 'AUDITOR')")
	@Operation(summary = "Get a period and current readiness")
	public PeriodResponse get(@PathVariable UUID clientId, @PathVariable UUID periodId) {
		return periodCloseService.get(clientId, periodId);
	}

	@GetMapping("/{periodId}/readiness")
	@PreAuthorize("hasAnyRole('ADMIN', 'ACCOUNTANT', 'AUDITOR')")
	@Operation(summary = "Close readiness: blockers, warnings, checklist, and period summaries")
	public PeriodReadinessResponse readiness(@PathVariable UUID clientId, @PathVariable UUID periodId) {
		return periodCloseService.readiness(clientId, periodId);
	}

	@PostMapping("/{periodId}/review")
	@PreAuthorize("hasAnyRole('ADMIN', 'ACCOUNTANT')")
	@Operation(summary = "Start month-end review (OPEN/REOPENED → IN_REVIEW). Does not lock writes.")
	public PeriodResponse startReview(@PathVariable UUID clientId, @PathVariable UUID periodId) {
		return periodCloseService.startReview(clientId, periodId);
	}

	@PostMapping("/{periodId}/close")
	@PreAuthorize("hasAnyRole('ADMIN', 'ACCOUNTANT')")
	@Operation(summary = "Close the period. Backend re-runs readiness; blockers reject with PERIOD_NOT_READY_TO_CLOSE.")
	public PeriodResponse close(
			@PathVariable UUID clientId,
			@PathVariable UUID periodId,
			@RequestBody(required = false) ClosePeriodRequest request
	) {
		return periodCloseService.close(clientId, periodId, request == null ? null : request.closeNote());
	}

	@PostMapping("/{periodId}/reopen")
	@PreAuthorize("hasRole('ADMIN')")
	@Operation(summary = "ADMIN only. Reopen a CLOSED period with a required reason. Recorded in audit.")
	public PeriodResponse reopen(
			@PathVariable UUID clientId,
			@PathVariable UUID periodId,
			@Valid @RequestBody ReopenPeriodRequest request
	) {
		return periodCloseService.reopen(clientId, periodId, request.reason());
	}
}
