package com.finance.platform.finance.application.service.invoicing;

import com.finance.platform.auth.infrastructure.security.SecurityUtils;
import com.finance.platform.core.exception.ResourceNotFoundException;
import com.finance.platform.finance.domain.model.Firm;
import com.finance.platform.finance.domain.model.invoicing.ArCustomer;
import com.finance.platform.finance.domain.model.invoicing.SalesInvoice;
import com.finance.platform.finance.domain.model.invoicing.SalesInvoiceLine;
import com.finance.platform.finance.infrastructure.persistence.FirmJpaRepository;
import com.finance.platform.finance.infrastructure.persistence.SalesInvoiceJpaRepository;
import com.finance.platform.finance.infrastructure.persistence.SalesInvoiceLineJpaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class InvoiceHtmlRenderer {

	private final SalesInvoiceJpaRepository invoiceRepository;
	private final SalesInvoiceLineJpaRepository lineRepository;
	private final ArCustomerService customerService;
	private final FirmJpaRepository firmRepository;

	@Transactional(readOnly = true)
	public String buildHtmlInvoice(UUID invoiceId) {
		UUID firmId = SecurityUtils.requireCurrentUser().getFirmId();
		SalesInvoice invoice = invoiceRepository.findByIdAndFirmId(invoiceId, firmId)
				.orElseThrow(() -> new ResourceNotFoundException("SalesInvoice", invoiceId));
		ArCustomer customer = customerService.requireCustomer(invoice.getCustomerId());
		Firm firm = firmRepository.findById(invoice.getFirmId()).orElse(null);
		List<SalesInvoiceLine> lines = lineRepository.findByInvoice_IdOrderByLineNoAsc(invoice.getId());
		StringBuilder html = new StringBuilder();
		html.append("<html><head><meta charset='utf-8'/><style>")
				.append("body{font-family:Arial,sans-serif;color:#111;margin:24px}")
				.append("table{border-collapse:collapse;width:100%}th,td{border:1px solid #ddd;padding:8px}")
				.append(".header{display:flex;justify-content:space-between;margin-bottom:24px}")
				.append("</style></head><body>");
		html.append("<div class='header'><div>");
		if (firm != null) {
			html.append("<h2>").append(escape(firm.getName())).append("</h2>");
			html.append("<div>Currency: ").append(escape(firm.getCurrencyCode())).append("</div>");
		}
		html.append("</div><div><h1>Invoice ").append(escape(invoice.getInvoiceNumber())).append("</h1></div></div>");
		html.append("<p><strong>Bill to:</strong> ").append(escape(customer.getName())).append("</p>");
		if (customer.getEmail() != null) {
			html.append("<p>").append(escape(customer.getEmail())).append("</p>");
		}
		html.append("<p>Issue date: ").append(invoice.getIssueDate()).append("</p>");
		html.append("<p>Due date: ").append(invoice.getDueDate()).append("</p>");
		html.append("<p>Currency: ").append(escape(invoice.getCurrency())).append("</p>");
		html.append("<table border='1' cellpadding='4'><tr><th>Description</th><th>Qty</th><th>Unit</th><th>Total</th></tr>");
		for (SalesInvoiceLine line : lines) {
			html.append("<tr><td>").append(line.getDescription()).append("</td><td>")
					.append(line.getQuantity()).append("</td><td>")
					.append(line.getUnitPrice()).append("</td><td>")
					.append(line.getLineTotal()).append("</td></tr>");
		}
		html.append("</table>");
		html.append("<p>Subtotal: ").append(invoice.getSubtotal()).append("</p>");
		html.append("<p>Tax: ").append(invoice.getTaxTotal()).append("</p>");
		html.append("<p><strong>Total: ").append(invoice.getTotal()).append(" ").append(invoice.getCurrency()).append("</strong></p>");
		if (invoice.getNotes() != null && !invoice.getNotes().isBlank()) {
			html.append("<p><strong>Notes:</strong> ").append(escape(invoice.getNotes())).append("</p>");
		}
		html.append("<p>Payment terms: Net ").append(customer.getPaymentTermsDays()).append(" days</p>");
		html.append("</body></html>");
		return html.toString();
	}

	private static String escape(String value) {
		if (value == null) {
			return "";
		}
		return value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
	}
}
