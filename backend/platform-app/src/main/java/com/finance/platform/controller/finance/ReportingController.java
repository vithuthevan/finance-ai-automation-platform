package com.finance.platform.controller.finance;

import com.finance.platform.reporting.api.ReportingFacade;
import com.finance.platform.reporting.application.ReportExportService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/clients/{clientId}/reports")
@RequiredArgsConstructor
public class ReportingController {

	private final ReportingFacade reportingFacade;
	private final ReportExportService reportExportService;

	@GetMapping("/profit-and-loss")
	@PreAuthorize("hasAnyRole('ADMIN', 'ACCOUNTANT', 'AUDITOR', 'BUSINESS_OWNER')")
	public ReportingFacade.PlSummary profitAndLoss(
			@PathVariable UUID clientId,
			@RequestParam LocalDate from,
			@RequestParam LocalDate to
	) {
		return reportingFacade.generatePlSummary(clientId, from, to);
	}

	@GetMapping("/profit-and-loss/comparison")
	@PreAuthorize("hasAnyRole('ADMIN', 'ACCOUNTANT', 'AUDITOR', 'BUSINESS_OWNER')")
	public ReportingFacade.PlComparison comparison(
			@PathVariable UUID clientId,
			@RequestParam LocalDate from,
			@RequestParam LocalDate to
	) {
		return reportingFacade.generatePlComparison(clientId, from, to);
	}

	@GetMapping("/dashboard")
	@PreAuthorize("hasAnyRole('ADMIN', 'ACCOUNTANT', 'AUDITOR', 'BUSINESS_OWNER')")
	public ReportingFacade.DashboardSummary dashboard(
			@PathVariable UUID clientId,
			@RequestParam LocalDate from,
			@RequestParam LocalDate to
	) {
		return reportingFacade.generateDashboard(clientId, from, to);
	}

	@GetMapping("/profit-and-loss/export")
	@PreAuthorize("hasAnyRole('ADMIN', 'ACCOUNTANT', 'AUDITOR')")
	public ResponseEntity<byte[]> export(
			@PathVariable UUID clientId,
			@RequestParam LocalDate from,
			@RequestParam LocalDate to,
			@RequestParam(defaultValue = "csv") String format
	) {
		ReportExportService.ExportFile file = reportExportService.exportPl(clientId, from, to, format);
		return ResponseEntity.ok()
				.contentType(MediaType.parseMediaType(file.contentType()))
				.header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + file.fileName() + "\"")
				.body(file.content());
	}
}
