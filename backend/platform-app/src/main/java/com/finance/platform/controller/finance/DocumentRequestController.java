package com.finance.platform.controller.finance;

import com.finance.platform.finance.application.service.DocumentRequestService;
import com.finance.platform.finance.domain.model.DocumentRequest;
import com.finance.platform.finance.domain.model.Receipt;
import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/clients/{clientId}/document-requests")
@RequiredArgsConstructor
public class DocumentRequestController {

	private final DocumentRequestService documentRequestService;

	@GetMapping
	@PreAuthorize("isAuthenticated()")
	public List<DocumentRequestView> list(@PathVariable UUID clientId) {
		return documentRequestService.list(clientId).stream().map(DocumentRequestView::from).toList();
	}

	@PostMapping
	@ResponseStatus(HttpStatus.CREATED)
	@PreAuthorize("hasAnyRole('ADMIN', 'ACCOUNTANT')")
	public DocumentRequestView create(@PathVariable UUID clientId, @RequestBody CreateRequest body) {
		return DocumentRequestView.from(documentRequestService.create(
				clientId, body.description(), body.documentType(), body.dueDate(), body.assigneeUserId()));
	}

	@PostMapping("/{requestId}/attach")
	@PreAuthorize("hasAnyRole('ADMIN', 'ACCOUNTANT', 'BUSINESS_OWNER')")
	public DocumentRequestView attach(
			@PathVariable UUID clientId,
			@PathVariable UUID requestId,
			@RequestBody AttachRequest body
	) {
		return DocumentRequestView.from(documentRequestService.attachUpload(clientId, requestId, body.documentId()));
	}

	@PostMapping("/{requestId}/complete")
	@PreAuthorize("hasAnyRole('ADMIN', 'ACCOUNTANT')")
	public DocumentRequestView complete(@PathVariable UUID clientId, @PathVariable UUID requestId) {
		return DocumentRequestView.from(documentRequestService.complete(clientId, requestId));
	}

	@PostMapping("/{requestId}/cancel")
	@PreAuthorize("hasAnyRole('ADMIN', 'ACCOUNTANT')")
	public DocumentRequestView cancel(@PathVariable UUID clientId, @PathVariable UUID requestId) {
		return DocumentRequestView.from(documentRequestService.cancel(clientId, requestId));
	}

	public record CreateRequest(@NotBlank String description, Receipt.DocumentType documentType, LocalDate dueDate, UUID assigneeUserId) {
	}

	public record AttachRequest(UUID documentId) {
	}

	public record DocumentRequestView(
			UUID id,
			UUID clientId,
			String description,
			Receipt.DocumentType documentType,
			LocalDate dueDate,
			DocumentRequest.RequestStatus status,
			UUID assigneeUserId,
			UUID uploadedDocumentId
	) {
		static DocumentRequestView from(DocumentRequest request) {
			return new DocumentRequestView(
					request.getId(),
					request.getClient().getId(),
					request.getDescription(),
					request.getDocumentType(),
					request.getDueDate(),
					request.getStatus(),
					request.getAssigneeUserId(),
					request.getUploadedDocumentId()
			);
		}
	}
}
