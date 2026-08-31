package com.finance.platform.controller.finance;

import com.finance.platform.core.exception.ErrorCodes;
import com.finance.platform.core.exception.ValidationException;
import com.finance.platform.reporting.api.ReportingFacade;
import com.finance.platform.reporting.application.ReportExportService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/clients/{clientId}/reports")
@RequiredArgsConstructor
@Tag(name = "Reports", description = "Approved-ledger profit and loss, summaries, comparisons, trends, and exports. Roles: ADMIN, ACCOUNTANT, AUDITOR, BUSINESS_OWNER (not UPLOAD_ONLY). Dates are ISO-8601 (yyyy-MM-dd).")
public class ReportingController {

	private final ReportingFacade reportingFacade;
	private final ReportExportService reportExportService;

	@GetMapping("/profit-and-loss")
	@PreAuthorize("hasAnyRole('ADMIN', 'ACCOUNTANT', 'AUDITOR', 'BUSINESS_OWNER')")
	@Operation(summary = "Profit and loss for an approved period")
	public ReportingFacade.PlSummary profitAndLoss(
			@PathVariable UUID clientId,
			@RequestParam LocalDate from,
			@RequestParam LocalDate to
	) {
		return reportingFacade.generatePlSummary(clientId, from, to);
	}

	@GetMapping("/profit-and-loss/comparison")
	@PreAuthorize("hasAnyRole('ADMIN', 'ACCOUNTANT', 'AUDITOR', 'BUSINESS_OWNER')")
	@Operation(summary = "Compare two periods. Omit compareFrom/compareTo to use the immediately previous equal-length period.")
	public ReportingFacade.PlComparison comparison(
			@PathVariable UUID clientId,
			@RequestParam LocalDate from,
			@RequestParam LocalDate to,
			@RequestParam(required = false) LocalDate compareFrom,
			@RequestParam(required = false) LocalDate compareTo
	) {
		if (compareFrom != null && compareTo != null) {
			return reportingFacade.generatePlComparison(clientId, from, to, compareFrom, compareTo);
		}
		if (compareFrom != null || compareTo != null) {
			throw new ValidationException(ErrorCodes.INVALID_REPORT_PERIOD, "compareTo", "Both comparison dates are required");
		}
		return reportingFacade.generatePlComparison(clientId, from, to);
	}

	@GetMapping("/income")
	@PreAuthorize("hasAnyRole('ADMIN', 'ACCOUNTANT', 'AUDITOR', 'BUSINESS_OWNER')")
	@Operation(summary = "Approved income summary by category and payment method")
	public ReportingFacade.IncomeSummary income(
			@PathVariable UUID clientId,
			@RequestParam LocalDate from,
			@RequestParam LocalDate to
	) {
		return reportingFacade.generateIncomeSummary(clientId, from, to);
	}

	@GetMapping("/expenses")
	@PreAuthorize("hasAnyRole('ADMIN', 'ACCOUNTANT', 'AUDITOR', 'BUSINESS_OWNER')")
	@Operation(summary = "Approved expense summary by category")
	public ReportingFacade.ExpenseSummary expenses(
			@PathVariable UUID clientId,
			@RequestParam LocalDate from,
			@RequestParam LocalDate to
	) {
		return reportingFacade.generateExpenseSummary(clientId, from, to);
	}

	@GetMapping("/cash-movement")
	@PreAuthorize("hasAnyRole('ADMIN', 'ACCOUNTANT', 'AUDITOR', 'BUSINESS_OWNER')")
	@Operation(summary = "Approved income minus approved expenses. Not an IFRS cash-flow statement.")
	public ReportingFacade.CashMovementSummary cashMovement(
			@PathVariable UUID clientId,
			@RequestParam LocalDate from,
			@RequestParam LocalDate to
	) {
		return reportingFacade.generateCashMovement(clientId, from, to);
	}

	@GetMapping("/trends")
	@PreAuthorize("hasAnyRole('ADMIN', 'ACCOUNTANT', 'AUDITOR', 'BUSINESS_OWNER')")
	@Operation(summary = "Monthly income, expense, and profit/loss trend. Maximum 36 months.")
	public List<ReportingFacade.MonthlyTrend> trends(
			@PathVariable UUID clientId,
			@RequestParam LocalDate from,
			@RequestParam LocalDate to
	) {
		return reportingFacade.generateMonthlyTrend(clientId, from, to);
	}

	@GetMapping("/top-categories")
	@PreAuthorize("hasAnyRole('ADMIN', 'ACCOUNTANT', 'AUDITOR', 'BUSINESS_OWNER')")
	@Operation(summary = "Largest approved income and expense categories")
	public ReportingFacade.TopCategories topCategories(
			@PathVariable UUID clientId,
			@RequestParam LocalDate from,
			@RequestParam LocalDate to
	) {
		return reportingFacade.generateTopCategories(clientId, from, to);
	}

	@GetMapping("/status-summary")
	@PreAuthorize("hasAnyRole('ADMIN', 'ACCOUNTANT', 'AUDITOR', 'BUSINESS_OWNER')")
	@Operation(summary = "Draft, approved, and void transaction counts")
	public ReportingFacade.TransactionStatusSummary statusSummary(@PathVariable UUID clientId) {
		return reportingFacade.generateStatusSummary(clientId);
	}

	@GetMapping("/document-support")
	@PreAuthorize("hasAnyRole('ADMIN', 'ACCOUNTANT', 'AUDITOR', 'BUSINESS_OWNER')")
	@Operation(summary = "Approved transactions with and without supporting documents")
	public ReportingFacade.DocumentSupportSummary documentSupport(
			@PathVariable UUID clientId,
			@RequestParam LocalDate from,
			@RequestParam LocalDate to
	) {
		return reportingFacade.generateDocumentSupport(clientId, from, to);
	}

	@GetMapping("/dashboard")
	@PreAuthorize("hasAnyRole('ADMIN', 'ACCOUNTANT', 'AUDITOR', 'BUSINESS_OWNER')")
	@Operation(summary = "Client reporting dashboard")
	public ReportingFacade.DashboardSummary dashboard(
			@PathVariable UUID clientId,
			@RequestParam LocalDate from,
			@RequestParam LocalDate to
	) {
		return reportingFacade.generateDashboard(clientId, from, to);
	}

	@GetMapping("/profit-and-loss/export")
	@PreAuthorize("hasAnyRole('ADMIN', 'ACCOUNTANT', 'AUDITOR', 'BUSINESS_OWNER')")
	@Operation(summary = "Export profit and loss as CSV or XLSX")
	public ResponseEntity<byte[]> exportPl(
			@PathVariable UUID clientId,
			@RequestParam LocalDate from,
			@RequestParam LocalDate to,
			@RequestParam(defaultValue = "csv") String format
	) {
		return toDownload(reportExportService.exportPl(clientId, from, to, format));
	}

	@GetMapping("/income/export")
	@PreAuthorize("hasAnyRole('ADMIN', 'ACCOUNTANT', 'AUDITOR', 'BUSINESS_OWNER')")
	@Operation(summary = "Export approved income transactions as CSV or XLSX")
	public ResponseEntity<byte[]> exportIncome(
			@PathVariable UUID clientId,
			@RequestParam LocalDate from,
			@RequestParam LocalDate to,
			@RequestParam(defaultValue = "csv") String format
	) {
		return toDownload(reportExportService.exportIncome(clientId, from, to, format));
	}

	@GetMapping("/expenses/export")
	@PreAuthorize("hasAnyRole('ADMIN', 'ACCOUNTANT', 'AUDITOR', 'BUSINESS_OWNER')")
	@Operation(summary = "Export approved expense transactions as CSV or XLSX")
	public ResponseEntity<byte[]> exportExpenses(
			@PathVariable UUID clientId,
			@RequestParam LocalDate from,
			@RequestParam LocalDate to,
			@RequestParam(defaultValue = "csv") String format
	) {
		return toDownload(reportExportService.exportExpenses(clientId, from, to, format));
	}

	private static ResponseEntity<byte[]> toDownload(ReportExportService.ExportFile file) {
		return ResponseEntity.ok()
				.contentType(MediaType.parseMediaType(file.contentType().contains("csv")
						? "text/csv"
						: file.contentType()))
				.header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
						.filename(file.fileName(), StandardCharsets.UTF_8)
						.build()
						.toString())
				.body(file.content());
	}
}
