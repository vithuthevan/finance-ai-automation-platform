package com.finance.platform.controller.finance;

import com.finance.platform.core.dto.PageResponse;
import com.finance.platform.finance.application.dto.CloseWorkQueueItemResponse;
import com.finance.platform.finance.application.service.PeriodCloseService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/close")
@RequiredArgsConstructor
@Tag(name = "Close", description = "Firm-level month-end work queue. Assignment-scoped for accountants. READY is derived (no blockers), not a persisted status.")
public class CloseWorkQueueController {

	private final PeriodCloseService periodCloseService;

	@GetMapping("/work-queue")
	@PreAuthorize("hasAnyRole('ADMIN', 'ACCOUNTANT', 'AUDITOR')")
	@Operation(summary = "Paginated close work queue for a calendar month")
	public PageResponse<CloseWorkQueueItemResponse> workQueue(
			@RequestParam int year,
			@RequestParam int month,
			@RequestParam(required = false) String status,
			@RequestParam(required = false) Boolean ready,
			@RequestParam(required = false) String q,
			@RequestParam(defaultValue = "0") int page,
			@RequestParam(defaultValue = "20") int size
	) {
		return periodCloseService.workQueue(year, month, status, ready, q, page, size);
	}
}
