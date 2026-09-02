package com.finance.platform.controller;

import com.finance.platform.finance.domain.model.Receipt;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/document-request-templates")
@Tag(name = "Document request templates", description = "Reusable firm-wide request templates.")
public class DocumentRequestTemplateController {

	@GetMapping
	@PreAuthorize("hasAnyRole('ADMIN', 'ACCOUNTANT')")
	public List<TemplateView> list() {
		return List.of(
				new TemplateView("Monthly Bank Statement", "Please upload the bank statement for the month.", Receipt.DocumentType.BANK_STATEMENT),
				new TemplateView("Missing Purchase Invoice", "Please upload the purchase invoice for this transaction.", Receipt.DocumentType.PURCHASE_INVOICE),
				new TemplateView("Missing Sales Invoice", "Please upload the sales invoice for this transaction.", Receipt.DocumentType.SALES_INVOICE),
				new TemplateView("Receipt for Bank Transaction", "Please upload a receipt supporting this bank transaction.", Receipt.DocumentType.RECEIPT)
		);
	}

	public record TemplateView(String title, String description, Receipt.DocumentType documentType) {
	}
}
