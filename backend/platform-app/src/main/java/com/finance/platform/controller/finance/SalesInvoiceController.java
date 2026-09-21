package com.finance.platform.controller.finance;

import com.finance.platform.core.dto.PageResponse;
import com.finance.platform.finance.application.dto.invoicing.CreateSalesInvoiceRequest;
import com.finance.platform.finance.application.dto.invoicing.SalesInvoiceResponse;
import com.finance.platform.finance.application.dto.invoicing.UpdateSalesInvoiceDraftRequest;
import com.finance.platform.finance.application.dto.invoicing.VoidInvoiceRequest;
import com.finance.platform.finance.application.service.invoicing.InvoicePdfService;
import com.finance.platform.finance.application.service.invoicing.SalesInvoiceService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
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

import java.util.UUID;

import org.springframework.http.HttpStatus;

@RestController
@RequestMapping("/api/v1/ar/invoices")
@RequiredArgsConstructor
public class SalesInvoiceController {

	private final SalesInvoiceService invoiceService;
	private final InvoicePdfService invoicePdfService;

	@GetMapping
	@PreAuthorize("hasAnyRole('ADMIN', 'ACCOUNTANT', 'AUDITOR')")
	public PageResponse<SalesInvoiceResponse> list(
			@RequestParam(required = false) UUID customerId,
			@RequestParam(required = false) String documentStatus,
			@RequestParam(required = false) String settlementStatus,
			@RequestParam(required = false) String query,
			@RequestParam(defaultValue = "0") int page,
			@RequestParam(defaultValue = "20") int size
	) {
		return invoiceService.list(customerId, documentStatus, settlementStatus, query, page, size);
	}

	@GetMapping("/{invoiceId}")
	@PreAuthorize("hasAnyRole('ADMIN', 'ACCOUNTANT', 'AUDITOR')")
	public SalesInvoiceResponse get(@PathVariable UUID invoiceId) {
		return invoiceService.get(invoiceId);
	}

	@PostMapping
	@ResponseStatus(HttpStatus.CREATED)
	@PreAuthorize("hasAnyRole('ADMIN', 'ACCOUNTANT')")
	public SalesInvoiceResponse create(@Valid @RequestBody CreateSalesInvoiceRequest request) {
		return invoiceService.createDraft(request);
	}

	@PutMapping("/{invoiceId}")
	@PreAuthorize("hasAnyRole('ADMIN', 'ACCOUNTANT')")
	public SalesInvoiceResponse updateDraft(
			@PathVariable UUID invoiceId,
			@Valid @RequestBody UpdateSalesInvoiceDraftRequest request
	) {
		return invoiceService.updateDraft(invoiceId, request);
	}

	@PostMapping("/{invoiceId}/issue")
	@PreAuthorize("hasAnyRole('ADMIN', 'ACCOUNTANT')")
	public SalesInvoiceResponse issue(@PathVariable UUID invoiceId) {
		return invoiceService.issue(invoiceId);
	}

	@PostMapping("/{invoiceId}/void")
	@PreAuthorize("hasAnyRole('ADMIN', 'ACCOUNTANT')")
	public SalesInvoiceResponse voidInvoice(
			@PathVariable UUID invoiceId,
			@Valid @RequestBody VoidInvoiceRequest request
	) {
		return invoiceService.voidInvoice(invoiceId, request);
	}

	@PostMapping("/{invoiceId}/send")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	@PreAuthorize("hasAnyRole('ADMIN', 'ACCOUNTANT')")
	public void send(@PathVariable UUID invoiceId) {
		invoiceService.sendEmail(invoiceId);
	}

	@GetMapping("/{invoiceId}/pdf")
	@PreAuthorize("hasAnyRole('ADMIN', 'ACCOUNTANT', 'AUDITOR')")
	public ResponseEntity<byte[]> pdf(@PathVariable UUID invoiceId) {
		SalesInvoiceResponse invoice = invoiceService.get(invoiceId);
		byte[] pdf = invoicePdfService.renderPdf(invoiceId);
		return ResponseEntity.ok()
				.header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + invoice.invoiceNumber() + ".pdf\"")
				.contentType(MediaType.APPLICATION_PDF)
				.body(pdf);
	}
}
