package com.finance.platform.finance.application.service.invoicing;

import com.finance.platform.auth.infrastructure.security.SecurityUtils;
import com.finance.platform.core.audit.AuditAction;
import com.finance.platform.core.audit.AuditEvent;
import com.finance.platform.core.audit.AuditLogger;
import com.finance.platform.core.audit.AuditResourceType;
import com.finance.platform.core.dto.PageResponse;
import com.finance.platform.core.exception.BusinessException;
import com.finance.platform.core.exception.ErrorCodes;
import com.finance.platform.core.exception.ResourceNotFoundException;
import com.finance.platform.core.exception.ValidationException;
import com.finance.platform.core.notification.EmailAttachment;
import com.finance.platform.core.notification.EmailService;
import com.finance.platform.finance.domain.model.Firm;
import com.finance.platform.finance.infrastructure.persistence.FirmJpaRepository;
import com.finance.platform.finance.application.dto.invoicing.CreateSalesInvoiceRequest;
import com.finance.platform.finance.application.dto.invoicing.SalesInvoiceLineRequest;
import com.finance.platform.finance.application.dto.invoicing.SalesInvoiceLineResponse;
import com.finance.platform.finance.application.dto.invoicing.SalesInvoiceResponse;
import com.finance.platform.finance.application.dto.invoicing.UpdateSalesInvoiceDraftRequest;
import com.finance.platform.finance.application.dto.invoicing.VoidInvoiceRequest;
import com.finance.platform.finance.application.invoicing.MoneyMath;
import com.finance.platform.finance.domain.model.invoicing.ArCustomer;
import com.finance.platform.finance.domain.model.invoicing.SalesInvoice;
import com.finance.platform.finance.domain.model.invoicing.SalesInvoiceLine;
import com.finance.platform.finance.infrastructure.persistence.SalesInvoiceJpaRepository;
import com.finance.platform.finance.infrastructure.persistence.SalesInvoiceLineJpaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class SalesInvoiceService {

	private final SalesInvoiceJpaRepository invoiceRepository;
	private final SalesInvoiceLineJpaRepository lineRepository;
	private final ArCustomerService customerService;
	private final InvoiceNumberSequenceService numberSequenceService;
	private final InvoiceSettlementService settlementService;
	private final EmailService emailService;
	private final FirmJpaRepository firmRepository;
	private final InvoicePdfService invoicePdfService;
	private final AuditLogger auditLogger;

	@Transactional(readOnly = true)
	public PageResponse<SalesInvoiceResponse> list(
			UUID customerId,
			String documentStatus,
			String settlementStatus,
			String query,
			int page,
			int size
	) {
		UUID firmId = SecurityUtils.requireCurrentUser().getFirmId();
		List<SalesInvoice> all = invoiceRepository.findByFirmIdOrderByCreatedAtDesc(firmId);
		List<SalesInvoice> filtered = all.stream()
				.filter(inv -> customerId == null || inv.getCustomerId().equals(customerId))
				.filter(inv -> documentStatus == null || inv.getStatus().name().equalsIgnoreCase(documentStatus))
				.filter(inv -> settlementStatus == null || inv.getSettlementStatus().name().equalsIgnoreCase(settlementStatus))
				.filter(inv -> query == null || query.isBlank()
						|| inv.getInvoiceNumber().toLowerCase(Locale.ROOT).contains(query.toLowerCase(Locale.ROOT)))
				.toList();
		int from = Math.min(page * size, filtered.size());
		int to = Math.min(from + size, filtered.size());
		List<SalesInvoiceResponse> content = filtered.subList(from, to).stream().map(this::toResponse).toList();
		return new PageResponse<>(content, page, size, filtered.size());
	}

	@Transactional(readOnly = true)
	public SalesInvoiceResponse get(UUID invoiceId) {
		return toResponse(requireInvoice(invoiceId));
	}

	@Transactional
	public SalesInvoiceResponse createDraft(CreateSalesInvoiceRequest request) {
		UUID firmId = SecurityUtils.requireCurrentUser().getFirmId();
		ArCustomer customer = customerService.requireCustomer(request.customerId());
		if (!customer.isActive()) {
			throw new ValidationException("customerId", "Customer is inactive");
		}
		SalesInvoice invoice = SalesInvoice.builder()
				.customerId(customer.getId())
				.invoiceNumber("DRAFT-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase(Locale.ROOT))
				.status(SalesInvoice.DocumentStatus.DRAFT)
				.settlementStatus(SalesInvoice.SettlementStatus.UNPAID)
				.currency(request.currency() != null ? request.currency() : "LKR")
				.notes(request.notes())
				.build();
		invoice.setFirmId(firmId);
		invoice = invoiceRepository.save(invoice);
		if (request.lines() != null && !request.lines().isEmpty()) {
			replaceLines(invoice, request.lines());
		}
		recalculateTotals(invoice);
		auditLogger.record(AuditEvent.fromTenant()
				.action(AuditAction.INCOME_CREATED)
				.resourceType(AuditResourceType.INCOME)
				.resourceId(invoice.getId())
				.metadata(Map.of("event", "sales_invoice_draft"))
				.build());
		return toResponse(invoice);
	}

	@Transactional
	public SalesInvoiceResponse updateDraft(UUID invoiceId, UpdateSalesInvoiceDraftRequest request) {
		SalesInvoice invoice = requireDraft(invoiceId);
		if (request.notes() != null) {
			invoice.setNotes(request.notes());
		}
		if (request.lines() != null) {
			replaceLines(invoice, request.lines());
			recalculateTotals(invoice);
		}
		return toResponse(invoiceRepository.save(invoice));
	}

	@Transactional
	public SalesInvoiceResponse issue(UUID invoiceId) {
		SalesInvoice invoice = requireDraft(invoiceId);
		List<SalesInvoiceLine> lines = lineRepository.findByInvoice_IdOrderByLineNoAsc(invoice.getId());
		if (lines.isEmpty()) {
			throw new ValidationException("lines", "At least one line is required to issue an invoice");
		}
		recalculateTotals(invoice);
		if (invoice.getTotal().compareTo(BigDecimal.ZERO) <= 0) {
			throw new ValidationException("total", "Invoice total must be positive");
		}
		ArCustomer customer = customerService.requireCustomer(invoice.getCustomerId());
		LocalDate issueDate = LocalDate.now();
		invoice.setInvoiceNumber(numberSequenceService.nextInvoiceNumber(invoice.getFirmId()));
		invoice.setIssueDate(issueDate);
		invoice.setDueDate(issueDate.plusDays(customer.getPaymentTermsDays()));
		invoice.setStatus(SalesInvoice.DocumentStatus.ISSUED);
		invoice.setSettlementStatus(SalesInvoice.SettlementStatus.UNPAID);
		settlementService.refreshSettlement(invoice);
		invoice = invoiceRepository.save(invoice);
		auditLogger.record(AuditEvent.fromTenant()
				.action(AuditAction.INCOME_UPDATED)
				.resourceType(AuditResourceType.INCOME)
				.resourceId(invoice.getId())
				.metadata(Map.of("event", "sales_invoice_issued", "invoiceNumber", invoice.getInvoiceNumber()))
				.build());
		return toResponse(invoice);
	}

	@Transactional
	public SalesInvoiceResponse voidInvoice(UUID invoiceId, VoidInvoiceRequest request) {
		SalesInvoice invoice = requireInvoice(invoiceId);
		if (invoice.getStatus() == SalesInvoice.DocumentStatus.VOID) {
			return toResponse(invoice);
		}
		if (invoice.getStatus() == SalesInvoice.DocumentStatus.ISSUED) {
			BigDecimal allocated = settlementService.allocatedTotal(invoice.getId());
			if (allocated.compareTo(BigDecimal.ZERO) > 0) {
				throw new BusinessException(ErrorCodes.VALIDATION_FAILED,
						"Cannot void an invoice with payment allocations; reverse payments first");
			}
		}
		invoice.setStatus(SalesInvoice.DocumentStatus.VOID);
		invoice.setSettlementStatus(SalesInvoice.SettlementStatus.UNPAID);
		invoice = invoiceRepository.save(invoice);
		auditLogger.record(AuditEvent.fromTenant()
				.action(AuditAction.INCOME_VOIDED)
				.resourceType(AuditResourceType.INCOME)
				.resourceId(invoice.getId())
				.metadata(Map.of("reason", request.reason()))
				.build());
		return toResponse(invoice);
	}

	@Transactional
	public void sendEmail(UUID invoiceId) {
		SalesInvoice invoice = requireInvoice(invoiceId);
		if (invoice.getStatus() != SalesInvoice.DocumentStatus.ISSUED) {
			throw new ValidationException("status", "Only issued invoices can be emailed");
		}
		ArCustomer customer = customerService.requireCustomer(invoice.getCustomerId());
		if (customer.getEmail() == null || customer.getEmail().isBlank()) {
			throw new ValidationException("email", "Customer has no email address on file");
		}
		String subject = "Invoice " + invoice.getInvoiceNumber();
		String body = buildPlainTextInvoice(invoice);
		byte[] pdf = invoicePdfService.renderPdf(invoice.getId());
		emailService.send(
				customer.getEmail(),
				subject,
				body,
				new EmailAttachment(invoice.getInvoiceNumber() + ".pdf", "application/pdf", pdf));
	}

	private String buildPlainTextInvoice(SalesInvoice invoice) {
		return "Please find your invoice " + invoice.getInvoiceNumber()
				+ " for " + invoice.getTotal() + " " + invoice.getCurrency()
				+ " due on " + invoice.getDueDate() + ".";
	}

	private void replaceLines(SalesInvoice invoice, List<SalesInvoiceLineRequest> lines) {
		lineRepository.deleteByInvoice_Id(invoice.getId());
		int lineNo = 1;
		for (SalesInvoiceLineRequest lineRequest : lines) {
			BigDecimal qty = MoneyMath.money(lineRequest.quantity());
			BigDecimal unit = MoneyMath.money(lineRequest.unitPrice());
			BigDecimal lineNet = MoneyMath.multiply(qty, unit);
			BigDecimal taxRate = lineRequest.taxRatePercent() != null
					? MoneyMath.money(lineRequest.taxRatePercent())
					: BigDecimal.ZERO;
			SalesInvoiceLine line = SalesInvoiceLine.builder()
					.invoice(invoice)
					.lineNo(lineNo++)
					.description(lineRequest.description().trim())
					.quantity(qty)
					.unitPrice(unit)
					.lineTotal(lineNet)
					.taxCode(lineRequest.taxCode())
					.taxRatePercent(taxRate)
					.build();
			lineRepository.save(line);
		}
	}

	private void recalculateTotals(SalesInvoice invoice) {
		List<SalesInvoiceLine> lines = lineRepository.findByInvoice_IdOrderByLineNoAsc(invoice.getId());
		BigDecimal subtotal = BigDecimal.ZERO;
		BigDecimal taxTotal = BigDecimal.ZERO;
		for (SalesInvoiceLine line : lines) {
			subtotal = MoneyMath.add(subtotal, line.getLineTotal());
			if (line.getTaxRatePercent().compareTo(BigDecimal.ZERO) > 0) {
				BigDecimal tax = MoneyMath.multiply(
						line.getLineTotal(),
						line.getTaxRatePercent().movePointLeft(2));
				taxTotal = MoneyMath.add(taxTotal, tax);
			}
		}
		invoice.setSubtotal(subtotal);
		invoice.setTaxTotal(taxTotal);
		invoice.setTotal(MoneyMath.add(subtotal, taxTotal));
	}

	SalesInvoice requireInvoice(UUID invoiceId) {
		UUID firmId = SecurityUtils.requireCurrentUser().getFirmId();
		return invoiceRepository.findByIdAndFirmId(invoiceId, firmId)
				.orElseThrow(() -> new ResourceNotFoundException("SalesInvoice", invoiceId));
	}

	private SalesInvoice requireDraft(UUID invoiceId) {
		SalesInvoice invoice = requireInvoice(invoiceId);
		if (invoice.getStatus() != SalesInvoice.DocumentStatus.DRAFT) {
			throw new BusinessException(ErrorCodes.VALIDATION_FAILED, "Only draft invoices can be edited");
		}
		return invoice;
	}

	private SalesInvoiceResponse toResponse(SalesInvoice invoice) {
		ArCustomer customer = customerService.requireCustomer(invoice.getCustomerId());
		List<SalesInvoiceLine> lines = lineRepository.findByInvoice_IdOrderByLineNoAsc(invoice.getId());
		BigDecimal allocated = settlementService.allocatedTotal(invoice.getId());
		BigDecimal outstanding = settlementService.outstanding(invoice);
		List<SalesInvoiceLineResponse> lineResponses = new ArrayList<>();
		for (SalesInvoiceLine line : lines) {
			lineResponses.add(new SalesInvoiceLineResponse(
					line.getId(),
					line.getLineNo(),
					line.getDescription(),
					line.getQuantity(),
					line.getUnitPrice(),
					line.getLineTotal(),
					line.getTaxCode(),
					line.getTaxRatePercent()
			));
		}
		return new SalesInvoiceResponse(
				invoice.getId(),
				invoice.getCustomerId(),
				customer.getName(),
				invoice.getInvoiceNumber(),
				invoice.getStatus().name(),
				invoice.getSettlementStatus().name(),
				invoice.getIssueDate(),
				invoice.getDueDate(),
				invoice.getCurrency(),
				invoice.getSubtotal(),
				invoice.getTaxTotal(),
				invoice.getTotal(),
				allocated,
				outstanding,
				invoice.getNotes(),
				invoice.getRowVersion(),
				invoice.getCreatedAt(),
				lineResponses
		);
	}
}
