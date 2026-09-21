package com.finance.platform.controller.finance;

import com.finance.platform.core.dto.PageResponse;
import com.finance.platform.finance.application.dto.BankAccountResponse;
import com.finance.platform.finance.application.dto.BankImportMappingRequest;
import com.finance.platform.finance.application.dto.BankImportPreviewResponse;
import com.finance.platform.finance.application.dto.BankImportResponse;
import com.finance.platform.finance.application.dto.BankTransactionResponse;
import com.finance.platform.finance.application.dto.ConfirmBankInvoicePaymentRequest;
import com.finance.platform.finance.application.dto.CreateBankAccountRequest;
import com.finance.platform.finance.application.dto.CreateDocumentRequestFromBankRequest;
import com.finance.platform.finance.application.dto.CreateExpenseFromBankRequest;
import com.finance.platform.finance.application.dto.CreateIncomeFromBankRequest;
import com.finance.platform.finance.application.dto.ExpenseResponse;
import com.finance.platform.finance.application.dto.IncomeResponse;
import com.finance.platform.finance.application.dto.ReconciliationSummaryResponse;
import com.finance.platform.finance.application.dto.UpdateBankAccountRequest;
import com.finance.platform.finance.application.service.BankAccountService;
import com.finance.platform.finance.application.service.BankReconciliationService;
import com.finance.platform.finance.domain.model.BankTransaction;
import com.finance.platform.finance.domain.model.DocumentRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
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
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/clients/{clientId}/bank")
@RequiredArgsConstructor
@Tag(name = "Bank", description = "Bank accounts, CSV import, and reconciliation. ADMIN/ACCOUNTANT operate; AUDITOR read-only.")
public class BankController {

	private final BankReconciliationService bankReconciliationService;
	private final BankAccountService bankAccountService;

	@GetMapping("/accounts")
	@PreAuthorize("hasAnyRole('ADMIN', 'ACCOUNTANT', 'AUDITOR')")
	public List<BankAccountResponse> listAccounts(
			@PathVariable UUID clientId,
			@RequestParam(defaultValue = "true") boolean activeOnly
	) {
		return bankAccountService.list(clientId, activeOnly);
	}

	@PostMapping("/accounts")
	@ResponseStatus(HttpStatus.CREATED)
	@PreAuthorize("hasAnyRole('ADMIN', 'ACCOUNTANT')")
	public BankAccountResponse createAccount(@PathVariable UUID clientId, @RequestBody CreateBankAccountRequest body) {
		return bankAccountService.create(clientId, body);
	}

	@PutMapping("/accounts/{accountId}")
	@PreAuthorize("hasAnyRole('ADMIN', 'ACCOUNTANT')")
	public BankAccountResponse updateAccount(
			@PathVariable UUID clientId,
			@PathVariable UUID accountId,
			@RequestBody UpdateBankAccountRequest body
	) {
		return bankAccountService.update(clientId, accountId, body);
	}

	@PostMapping("/imports/preview")
	@PreAuthorize("hasAnyRole('ADMIN', 'ACCOUNTANT')")
	@Operation(summary = "Preview CSV import without persisting transactions")
	public BankImportPreviewResponse previewImport(
			@PathVariable UUID clientId,
			@RequestParam UUID bankAccountId,
			@RequestParam("file") MultipartFile file,
			@RequestParam(defaultValue = "0") int dateColumn,
			@RequestParam(defaultValue = "1") int descriptionColumn,
			@RequestParam(defaultValue = "2") int referenceColumn,
			@RequestParam(defaultValue = "3") int debitColumn,
			@RequestParam(defaultValue = "4") int creditColumn,
			@RequestParam(defaultValue = "5") int balanceColumn,
			@RequestParam(required = false) Integer amountColumn,
			@RequestParam(defaultValue = "AUTO") String dateFormat,
			@RequestParam(defaultValue = "true") boolean headerRow
	) throws IOException {
		return bankReconciliationService.previewImport(
				clientId,
				bankAccountId,
				file.getBytes(),
				new BankImportMappingRequest(
						dateColumn, descriptionColumn, referenceColumn, debitColumn, creditColumn,
						balanceColumn, amountColumn, dateFormat, headerRow, null).toMapping());
	}

	@PostMapping("/imports")
	@ResponseStatus(HttpStatus.CREATED)
	@PreAuthorize("hasAnyRole('ADMIN', 'ACCOUNTANT')")
	public BankImportResponse importCsv(
			@PathVariable UUID clientId,
			@RequestParam UUID bankAccountId,
			@RequestParam("file") MultipartFile file,
			@RequestParam(defaultValue = "0") int dateColumn,
			@RequestParam(defaultValue = "1") int descriptionColumn,
			@RequestParam(defaultValue = "2") int referenceColumn,
			@RequestParam(defaultValue = "3") int debitColumn,
			@RequestParam(defaultValue = "4") int creditColumn,
			@RequestParam(defaultValue = "5") int balanceColumn,
			@RequestParam(required = false) Integer amountColumn,
			@RequestParam(defaultValue = "AUTO") String dateFormat,
			@RequestParam(defaultValue = "true") boolean headerRow,
			@RequestParam(required = false) String profileName
	) throws IOException {
		return bankReconciliationService.importCsv(
				clientId,
				bankAccountId,
				file.getOriginalFilename(),
				file.getBytes(),
				new BankImportMappingRequest(
						dateColumn, descriptionColumn, referenceColumn, debitColumn, creditColumn,
						balanceColumn, amountColumn, dateFormat, headerRow, profileName).toMapping(),
				profileName);
	}

	@GetMapping("/imports")
	@PreAuthorize("hasAnyRole('ADMIN', 'ACCOUNTANT', 'AUDITOR')")
	public List<BankImportResponse> listImports(
			@PathVariable UUID clientId,
			@RequestParam(required = false) UUID bankAccountId
	) {
		return bankReconciliationService.listImports(clientId, bankAccountId);
	}

	@GetMapping("/transactions")
	@PreAuthorize("hasAnyRole('ADMIN', 'ACCOUNTANT', 'AUDITOR')")
	public PageResponse<BankTransactionResponse> list(
			@PathVariable UUID clientId,
			@RequestParam(required = false) UUID bankAccountId,
			@RequestParam(required = false) BankTransaction.MatchStatus status,
			@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
			@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
			@RequestParam(required = false) String q,
			@RequestParam(defaultValue = "0") int page,
			@RequestParam(defaultValue = "20") int size
	) {
		return bankReconciliationService.list(clientId, bankAccountId, status, from, to, q, page, size);
	}

	@GetMapping("/dashboard")
	@PreAuthorize("hasAnyRole('ADMIN', 'ACCOUNTANT', 'AUDITOR')")
	public Map<String, Long> dashboard(@PathVariable UUID clientId) {
		return bankReconciliationService.dashboard(clientId);
	}

	@GetMapping("/reconciliation/summary")
	@PreAuthorize("hasAnyRole('ADMIN', 'ACCOUNTANT', 'AUDITOR')")
	public ReconciliationSummaryResponse summary(
			@PathVariable UUID clientId,
			@RequestParam(required = false) UUID bankAccountId,
			@RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
			@RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to
	) {
		return bankReconciliationService.summary(clientId, bankAccountId, from, to);
	}

	@PostMapping("/transactions/{bankTransactionId}/confirm")
	@PreAuthorize("hasAnyRole('ADMIN', 'ACCOUNTANT')")
	public BankTransactionResponse confirm(
			@PathVariable UUID clientId,
			@PathVariable UUID bankTransactionId,
			@RequestBody ConfirmRequest body
	) {
		return bankReconciliationService.confirmMatch(clientId, bankTransactionId, body.expenseId(), body.incomeId());
	}

	@PostMapping("/transactions/{bankTransactionId}/confirm-invoice-payment")
	@PreAuthorize("hasAnyRole('ADMIN', 'ACCOUNTANT')")
	public BankTransactionResponse confirmInvoicePayment(
			@PathVariable UUID clientId,
			@PathVariable UUID bankTransactionId,
			@jakarta.validation.Valid @RequestBody ConfirmBankInvoicePaymentRequest body
	) {
		return bankReconciliationService.confirmInvoicePayment(clientId, bankTransactionId, body);
	}

	@PostMapping("/transactions/{bankTransactionId}/reject")
	@PreAuthorize("hasAnyRole('ADMIN', 'ACCOUNTANT')")
	public BankTransactionResponse reject(
			@PathVariable UUID clientId,
			@PathVariable UUID bankTransactionId,
			@RequestBody RejectRequest body
	) {
		return bankReconciliationService.rejectSuggestion(clientId, bankTransactionId, body.matchId());
	}

	@PostMapping("/transactions/{bankTransactionId}/unmatch")
	@PreAuthorize("hasAnyRole('ADMIN', 'ACCOUNTANT')")
	public BankTransactionResponse unmatch(@PathVariable UUID clientId, @PathVariable UUID bankTransactionId) {
		return bankReconciliationService.unmatch(clientId, bankTransactionId);
	}

	@PostMapping("/transactions/{bankTransactionId}/ignore")
	@PreAuthorize("hasAnyRole('ADMIN', 'ACCOUNTANT')")
	public BankTransactionResponse ignore(
			@PathVariable UUID clientId,
			@PathVariable UUID bankTransactionId,
			@RequestBody IgnoreRequest body
	) {
		return bankReconciliationService.ignore(clientId, bankTransactionId, body.reason());
	}

	@PostMapping("/transactions/{bankTransactionId}/suggestions/regenerate")
	@PreAuthorize("hasAnyRole('ADMIN', 'ACCOUNTANT')")
	public BankTransactionResponse regenerate(
			@PathVariable UUID clientId,
			@PathVariable UUID bankTransactionId
	) {
		return bankReconciliationService.regenerateSuggestions(clientId, bankTransactionId);
	}

	@PostMapping("/transactions/{bankTransactionId}/create-expense")
	@ResponseStatus(HttpStatus.CREATED)
	@PreAuthorize("hasAnyRole('ADMIN', 'ACCOUNTANT')")
	public ExpenseResponse createExpense(
			@PathVariable UUID clientId,
			@PathVariable UUID bankTransactionId,
			@RequestBody CreateExpenseFromBankRequest body
	) {
		return bankReconciliationService.createExpenseFromBank(clientId, bankTransactionId, body);
	}

	@PostMapping("/transactions/{bankTransactionId}/create-income")
	@ResponseStatus(HttpStatus.CREATED)
	@PreAuthorize("hasAnyRole('ADMIN', 'ACCOUNTANT')")
	public IncomeResponse createIncome(
			@PathVariable UUID clientId,
			@PathVariable UUID bankTransactionId,
			@RequestBody CreateIncomeFromBankRequest body
	) {
		return bankReconciliationService.createIncomeFromBank(clientId, bankTransactionId, body);
	}

	@PostMapping("/transactions/{bankTransactionId}/request-document")
	@ResponseStatus(HttpStatus.CREATED)
	@PreAuthorize("hasAnyRole('ADMIN', 'ACCOUNTANT')")
	public DocumentRequestView requestDocument(
			@PathVariable UUID clientId,
			@PathVariable UUID bankTransactionId,
			@RequestBody CreateDocumentRequestFromBankRequest body
	) {
		DocumentRequest request = bankReconciliationService.createDocumentRequestFromBank(clientId, bankTransactionId, body);
		return new DocumentRequestView(request.getId(), request.getDescription(), request.getStatus().name());
	}

	public record ConfirmRequest(UUID expenseId, UUID incomeId) {
	}

	public record RejectRequest(UUID matchId) {
	}

	public record IgnoreRequest(String reason) {
	}

	public record DocumentRequestView(UUID id, String description, String status) {
	}
}
