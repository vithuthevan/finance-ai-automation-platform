package com.finance.platform.controller;

import com.finance.platform.core.dto.PageResponse;
import com.finance.platform.finance.application.dto.DocumentRequestCenterItemResponse;
import com.finance.platform.finance.application.service.DocumentRequestCenterService;
import com.finance.platform.finance.domain.model.DocumentRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/practice/document-requests")
@RequiredArgsConstructor
@Tag(name = "Document request center", description = "Firm-wide operational view of client document requests.")
public class DocumentRequestCenterController {

	private final DocumentRequestCenterService requestCenterService;

	@GetMapping
	@PreAuthorize("hasAnyRole('ADMIN', 'ACCOUNTANT')")
	@Operation(summary = "List document requests across accessible clients")
	public PageResponse<DocumentRequestCenterItemResponse> list(
			@RequestParam(required = false) DocumentRequest.RequestStatus status,
			@RequestParam(required = false) Boolean overdueOnly,
			@RequestParam(defaultValue = "0") int page,
			@RequestParam(defaultValue = "50") int size
	) {
		return requestCenterService.list(status, overdueOnly, page, size);
	}
}
