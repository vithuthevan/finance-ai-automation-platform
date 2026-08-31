package com.finance.platform.controller.finance;

import com.finance.platform.reporting.api.ReportingFacade;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/reports")
@RequiredArgsConstructor
@Tag(name = "Practice reports", description = "Workload dashboard for the accounting firm. Does not sum client finances.")
public class PracticeReportingController {

	private final ReportingFacade reportingFacade;

	@GetMapping("/practice")
	@PreAuthorize("hasAnyRole('ADMIN', 'ACCOUNTANT')")
	@Operation(summary = "Practice workload: active clients, drafts, documents needing review")
	public ReportingFacade.PracticeDashboard practice() {
		return reportingFacade.generatePracticeDashboard();
	}
}
