package com.finance.platform.controller.finance;

import com.finance.platform.core.dto.PageResponse;
import com.finance.platform.finance.application.dto.invoicing.ArAgeingBuckets;
import com.finance.platform.finance.application.dto.invoicing.ArCustomerBalanceResponse;
import com.finance.platform.finance.application.dto.invoicing.ArSummaryResponse;
import com.finance.platform.finance.application.dto.invoicing.SalesInvoiceResponse;
import com.finance.platform.finance.application.service.invoicing.AccountsReceivableService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/ar/receivables")
@RequiredArgsConstructor
public class AccountsReceivableController {

	private final AccountsReceivableService receivableService;

	@GetMapping("/summary")
	@PreAuthorize("hasAnyRole('ADMIN', 'ACCOUNTANT', 'AUDITOR')")
	public ArSummaryResponse summary() {
		return receivableService.summary();
	}

	@GetMapping("/invoices")
	@PreAuthorize("hasAnyRole('ADMIN', 'ACCOUNTANT', 'AUDITOR')")
	public PageResponse<SalesInvoiceResponse> invoices(
			@RequestParam(defaultValue = "0") int page,
			@RequestParam(defaultValue = "20") int size
	) {
		return receivableService.receivableInvoices(page, size);
	}

	@GetMapping("/ageing")
	@PreAuthorize("hasAnyRole('ADMIN', 'ACCOUNTANT', 'AUDITOR')")
	public ArAgeingBuckets ageing() {
		return receivableService.ageing();
	}

	@GetMapping("/customer-balances")
	@PreAuthorize("hasAnyRole('ADMIN', 'ACCOUNTANT', 'AUDITOR')")
	public List<ArCustomerBalanceResponse> customerBalances() {
		return receivableService.customerBalances();
	}

	@GetMapping("/overdue")
	@PreAuthorize("hasAnyRole('ADMIN', 'ACCOUNTANT', 'AUDITOR')")
	public PageResponse<SalesInvoiceResponse> overdue(
			@RequestParam(defaultValue = "0") int page,
			@RequestParam(defaultValue = "20") int size
	) {
		return receivableService.overdue(page, size);
	}
}
