package com.finance.platform.controller.finance;

import com.finance.platform.core.dto.PageResponse;
import com.finance.platform.finance.application.service.DocumentRequestService;
import com.finance.platform.finance.domain.model.DocumentRequest;
import com.finance.platform.finance.domain.model.Receipt;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.NotBlank;
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
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/clients/{clientId}/document-requests")
@RequiredArgsConstructor
@Tag(name = "Document requests", description = "Missing-evidence requests. ACCOUNTANT/ADMIN create, complete, cancel. BUSINESS_OWNER and UPLOAD_ONLY upload against OPEN requests. Upload marks UPLOADED; accountant completion is required.")
public class DocumentRequestController {

	private final DocumentRequestService documentRequestService;

	@GetMapping
	@PreAuthorize("isAuthenticated()")
	@Operation(summary = "List document requests for a client")
	public PageResponse<DocumentRequestView> list(
			@PathVariable UUID clientId,
			@RequestParam(required = false) DocumentRequest.RequestStatus status,
			@RequestParam(required = false) UUID periodId,
			@RequestParam(defaultValue = "0") int page,
			@RequestParam(defaultValue = "50") int size
	) {
		PageResponse<DocumentRequest> pageResult = documentRequestService.list(clientId, status, periodId, page, size);
		return new PageResponse<>(
				pageResult.content().stream().map(DocumentRequestView::from).toList(),
				pageResult.page(),
				pageResult.size(),
				pageResult.totalElements()
		);
	}

	@PostMapping
	@ResponseStatus(HttpStatus.CREATED)
	@PreAuthorize("hasAnyRole('ADMIN', 'ACCOUNTANT')")
	@Operation(summary = "Request missing evidence from the client owner")
	public DocumentRequestView create(@PathVariable UUID clientId, @RequestBody CreateRequest body) {
		return DocumentRequestView.from(documentRequestService.create(
				clientId, body.description(), body.documentType(), body.dueDate(), body.assigneeUserId(), body.periodId()));
	}

	@PostMapping("/{requestId}/attach")
	@PreAuthorize("hasAnyRole('ADMIN', 'ACCOUNTANT', 'BUSINESS_OWNER')")
	@Operation(summary = "Link an already uploaded document to a request (status becomes UPLOADED)")
	public DocumentRequestView attach(
			@PathVariable UUID clientId,
			@PathVariable UUID requestId,
			@RequestBody AttachRequest body
	) {
		return DocumentRequestView.from(documentRequestService.attachUpload(clientId, requestId, body.documentId()));
	}

	@PostMapping(value = "/{requestId}/upload", consumes = "multipart/form-data")
	@PreAuthorize("hasAnyRole('ADMIN', 'ACCOUNTANT', 'BUSINESS_OWNER')")
	@Operation(summary = "Upload a file against a request. Marks UPLOADED, not COMPLETED.")
	public DocumentRequestView upload(
			@PathVariable UUID clientId,
			@PathVariable UUID requestId,
			@RequestParam("file") MultipartFile file
	) throws IOException {
		return DocumentRequestView.from(documentRequestService.uploadAgainstRequest(
				clientId, requestId, file.getBytes(), file.getOriginalFilename(), file.getContentType()));
	}

	@PostMapping("/{requestId}/complete")
	@PreAuthorize("hasAnyRole('ADMIN', 'ACCOUNTANT')")
	@Operation(summary = "Accountant accepts the uploaded evidence")
	public DocumentRequestView complete(@PathVariable UUID clientId, @PathVariable UUID requestId) {
		return DocumentRequestView.from(documentRequestService.complete(clientId, requestId));
	}

	@PostMapping("/{requestId}/cancel")
	@PreAuthorize("hasAnyRole('ADMIN', 'ACCOUNTANT')")
	@Operation(summary = "Cancel an obsolete request without deleting history")
	public DocumentRequestView cancel(@PathVariable UUID clientId, @PathVariable UUID requestId) {
		return DocumentRequestView.from(documentRequestService.cancel(clientId, requestId));
	}

	public record CreateRequest(
			@NotBlank String description,
			Receipt.DocumentType documentType,
			LocalDate dueDate,
			UUID assigneeUserId,
			UUID periodId
	) {
	}

	public record AttachRequest(UUID documentId) {
	}

	public record DocumentRequestView(
			UUID id,
			UUID clientId,
			UUID periodId,
			String description,
			Receipt.DocumentType documentType,
			LocalDate dueDate,
			DocumentRequest.RequestStatus status,
			UUID assigneeUserId,
			UUID uploadedDocumentId,
			Instant completedAt
	) {
		static DocumentRequestView from(DocumentRequest request) {
			return new DocumentRequestView(
					request.getId(),
					request.getClient().getId(),
					request.getPeriod() == null ? null : request.getPeriod().getId(),
					request.getDescription(),
					request.getDocumentType(),
					request.getDueDate(),
					request.getStatus(),
					request.getAssigneeUserId(),
					request.getUploadedDocumentId(),
					request.getCompletedAt()
			);
		}
	}
}
