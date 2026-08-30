package com.finance.platform.controller.finance;

import com.finance.platform.core.dto.PageResponse;
import com.finance.platform.finance.application.dto.BankTransactionResponse;
import com.finance.platform.finance.application.service.BankReconciliationService;
import com.finance.platform.finance.domain.model.BankImport;
import com.finance.platform.finance.domain.model.BankTransaction;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/clients/{clientId}/bank")
@RequiredArgsConstructor
public class BankController {

	private final BankReconciliationService bankReconciliationService;

	@PostMapping("/imports")
	@ResponseStatus(HttpStatus.CREATED)
	@PreAuthorize("hasAnyRole('ADMIN', 'ACCOUNTANT')")
	public BankImportView importCsv(
			@PathVariable UUID clientId,
			@RequestParam("file") MultipartFile file,
			@RequestParam(defaultValue = "0") int dateColumn,
			@RequestParam(defaultValue = "1") int descriptionColumn,
			@RequestParam(defaultValue = "2") int referenceColumn,
			@RequestParam(defaultValue = "3") int debitColumn,
			@RequestParam(defaultValue = "4") int creditColumn,
			@RequestParam(defaultValue = "5") int balanceColumn
	) throws IOException {
		BankImport imported = bankReconciliationService.importCsv(
				clientId,
				file.getOriginalFilename(),
				file.getBytes(),
				new BankReconciliationService.CsvMapping(dateColumn, descriptionColumn, referenceColumn, debitColumn, creditColumn, balanceColumn)
		);
		return new BankImportView(imported.getId(), imported.getFileName(), imported.getRowCount(), imported.getStatus());
	}

	@GetMapping("/transactions")
	@PreAuthorize("hasAnyRole('ADMIN', 'ACCOUNTANT', 'AUDITOR')")
	public PageResponse<BankTransactionResponse> list(
			@PathVariable UUID clientId,
			@RequestParam(required = false) BankTransaction.MatchStatus status,
			@RequestParam(defaultValue = "0") int page,
			@RequestParam(defaultValue = "20") int size
	) {
		return bankReconciliationService.list(clientId, status, page, size);
	}

	@GetMapping("/dashboard")
	@PreAuthorize("hasAnyRole('ADMIN', 'ACCOUNTANT', 'AUDITOR')")
	public Map<String, Long> dashboard(@PathVariable UUID clientId) {
		return bankReconciliationService.dashboard(clientId);
	}

	@PostMapping("/transactions/{bankTransactionId}/confirm")
	@PreAuthorize("hasAnyRole('ADMIN', 'ACCOUNTANT')")
	public BankTransactionResponse confirm(
			@PathVariable UUID clientId,
			@PathVariable UUID bankTransactionId,
			@RequestBody ConfirmRequest body
	) {
		return BankTransactionResponse.from(bankReconciliationService.confirmMatch(clientId, bankTransactionId, body.expenseId(), body.incomeId()));
	}

	@PostMapping("/transactions/{bankTransactionId}/status")
	@PreAuthorize("hasAnyRole('ADMIN', 'ACCOUNTANT')")
	public BankTransactionResponse markStatus(
			@PathVariable UUID clientId,
			@PathVariable UUID bankTransactionId,
			@RequestBody StatusRequest body
	) {
		return BankTransactionResponse.from(bankReconciliationService.markStatus(clientId, bankTransactionId, body.status()));
	}

	public record ConfirmRequest(UUID expenseId, UUID incomeId) {
	}

	public record StatusRequest(BankTransaction.MatchStatus status) {
	}

	public record BankImportView(UUID id, String fileName, int rowCount, String status) {
	}
}
