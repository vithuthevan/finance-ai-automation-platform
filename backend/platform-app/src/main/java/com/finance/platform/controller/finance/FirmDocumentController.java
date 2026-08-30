package com.finance.platform.controller.finance;

import com.finance.platform.core.dto.PageResponse;
import com.finance.platform.finance.application.dto.DocumentResponse;
import com.finance.platform.finance.application.service.DocumentService;
import com.finance.platform.finance.domain.model.Receipt;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/documents")
@RequiredArgsConstructor
public class FirmDocumentController {

	private final DocumentService documentService;

	@GetMapping
	@PreAuthorize("isAuthenticated()")
	public PageResponse<DocumentResponse> list(
			@RequestParam(required = false) UUID clientId,
			@RequestParam(required = false) Receipt.ReceiptStatus status,
			@RequestParam(required = false) Receipt.DocumentType documentType,
			@RequestParam(required = false) UUID uploadedBy,
			@RequestParam(required = false) LocalDate from,
			@RequestParam(required = false) LocalDate to,
			@RequestParam(required = false) Boolean linked,
			@RequestParam(defaultValue = "0") int page,
			@RequestParam(defaultValue = "20") int size
	) {
		return documentService.listInbox(clientId, status, documentType, uploadedBy, from, to, linked, page, size);
	}
}
