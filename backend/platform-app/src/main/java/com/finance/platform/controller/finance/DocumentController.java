package com.finance.platform.controller.finance;

import com.finance.platform.core.dto.PageResponse;
import com.finance.platform.finance.application.dto.AcceptSuggestionRequest;
import com.finance.platform.finance.application.dto.DocumentResponse;
import com.finance.platform.finance.application.dto.ModifySuggestionRequest;
import com.finance.platform.finance.application.dto.RejectDocumentRequest;
import com.finance.platform.finance.application.dto.RejectSuggestionRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import com.finance.platform.finance.application.dto.UnlinkDocumentRequest;
import com.finance.platform.finance.application.service.DocumentReviewService;
import com.finance.platform.finance.application.service.DocumentService;
import com.finance.platform.finance.domain.model.Receipt;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
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
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/clients/{clientId}/documents")
@RequiredArgsConstructor
@Tag(name = "Documents", description = "Upload, extraction review, suggestion accept/reject, and retry. Accept creates DRAFT only.")
public class DocumentController {

	private final DocumentService documentService;
	private final DocumentReviewService documentReviewService;

	@PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
	@ResponseStatus(HttpStatus.CREATED)
	@PreAuthorize("hasAnyRole('ADMIN', 'ACCOUNTANT', 'BUSINESS_OWNER')")
	public DocumentResponse upload(
			@PathVariable UUID clientId,
			@RequestParam("file") MultipartFile file,
			@RequestParam(required = false, defaultValue = "RECEIPT") Receipt.DocumentType documentType,
			@RequestParam(required = false) String description,
			@RequestParam(defaultValue = "false") boolean allowDuplicate
	) throws IOException {
		return documentService.upload(
				clientId,
				file.getBytes(),
				file.getOriginalFilename(),
				file.getContentType(),
				documentType,
				description,
				allowDuplicate
		);
	}

	@GetMapping
	@PreAuthorize("isAuthenticated()")
	public PageResponse<DocumentResponse> list(
			@PathVariable UUID clientId,
			@RequestParam(required = false) Receipt.ReceiptStatus status,
			@RequestParam(required = false) Receipt.DocumentType documentType,
			@RequestParam(required = false) UUID uploadedBy,
			@RequestParam(required = false) LocalDate from,
			@RequestParam(required = false) LocalDate to,
			@RequestParam(required = false) Boolean linked,
			@RequestParam(defaultValue = "0") int page,
			@RequestParam(defaultValue = "20") int size
	) {
		return documentService.list(clientId, status, documentType, uploadedBy, from, to, linked, page, size);
	}

	@GetMapping("/{documentId}")
	@PreAuthorize("isAuthenticated()")
	public DocumentResponse get(@PathVariable UUID clientId, @PathVariable UUID documentId) {
		return documentService.get(clientId, documentId);
	}

	@GetMapping("/{documentId}/content")
	@PreAuthorize("isAuthenticated()")
	public ResponseEntity<InputStreamResource> download(
			@PathVariable UUID clientId,
			@PathVariable UUID documentId
	) {
		DocumentService.StoredDocument stored = documentService.openContent(clientId, documentId);
		boolean preview = isPreviewable(stored.mimeType());
		ContentDisposition disposition = (preview ? ContentDisposition.inline() : ContentDisposition.attachment())
				.filename(stored.fileName() == null ? "document" : stored.fileName(), StandardCharsets.UTF_8)
				.build();
		return ResponseEntity.ok()
				.contentType(MediaType.parseMediaType(stored.mimeType()))
				.header(HttpHeaders.CONTENT_DISPOSITION, disposition.toString())
				.body(new InputStreamResource(stored.content()));
	}

	@PostMapping("/{documentId}/reject")
	@PreAuthorize("hasAnyRole('ADMIN', 'ACCOUNTANT')")
	public DocumentResponse reject(
			@PathVariable UUID clientId,
			@PathVariable UUID documentId,
			@Valid @RequestBody RejectDocumentRequest request
	) {
		return documentService.reject(clientId, documentId, request.reason());
	}

	@PostMapping("/{documentId}/mark-duplicate")
	@PreAuthorize("hasAnyRole('ADMIN', 'ACCOUNTANT')")
	public DocumentResponse markDuplicate(@PathVariable UUID clientId, @PathVariable UUID documentId) {
		return documentService.markDuplicate(clientId, documentId);
	}

	@PostMapping("/{documentId}/transactions")
	@PreAuthorize("hasAnyRole('ADMIN', 'ACCOUNTANT')")
	public DocumentResponse createTransaction(
			@PathVariable UUID clientId,
			@PathVariable UUID documentId,
			@Valid @RequestBody ModifySuggestionRequest request
	) {
		return documentReviewService.createFromDocument(clientId, documentId, request);
	}

	@PostMapping("/{documentId}/unlink")
	@PreAuthorize("hasAnyRole('ADMIN', 'ACCOUNTANT')")
	public DocumentResponse unlink(
			@PathVariable UUID clientId,
			@PathVariable UUID documentId,
			@Valid @RequestBody UnlinkDocumentRequest request
	) {
		return documentService.unlink(clientId, documentId, request.expenseId(), request.incomeId());
	}

	@DeleteMapping("/{documentId}")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	@PreAuthorize("hasAnyRole('ADMIN', 'ACCOUNTANT')")
	public void delete(@PathVariable UUID clientId, @PathVariable UUID documentId) {
		documentService.delete(clientId, documentId);
	}

	@PostMapping("/{documentId}/retry-processing")
	@PreAuthorize("hasAnyRole('ADMIN', 'ACCOUNTANT')")
	@Operation(summary = "Retry failed or pending AI extraction")
	public DocumentResponse retryProcessing(@PathVariable UUID clientId, @PathVariable UUID documentId) {
		return documentService.retryProcessing(clientId, documentId);
	}

	@PostMapping("/{documentId}/review/reject-suggestion")
	@PreAuthorize("hasAnyRole('ADMIN', 'ACCOUNTANT')")
	@Operation(summary = "Reject the AI suggestion without rejecting the document")
	public DocumentResponse rejectSuggestion(
			@PathVariable UUID clientId,
			@PathVariable UUID documentId,
			@RequestBody(required = false) RejectSuggestionRequest request
	) {
		return documentReviewService.rejectSuggestion(clientId, documentId, request != null ? request.note() : null);
	}

	@PostMapping("/{documentId}/review/accept")
	@PreAuthorize("hasAnyRole('ADMIN', 'ACCOUNTANT')")
	@Operation(summary = "Accept or modify an AI suggestion. Creates a DRAFT transaction only.")
	public DocumentResponse acceptSuggestion(
			@PathVariable UUID clientId,
			@PathVariable UUID documentId,
			@RequestBody(required = false) AcceptSuggestionRequest request
	) {
		return documentReviewService.accept(clientId, documentId, request);
	}

	@PostMapping("/{documentId}/review/modify")
	@PreAuthorize("hasAnyRole('ADMIN', 'ACCOUNTANT')")
	public DocumentResponse modifySuggestion(
			@PathVariable UUID clientId,
			@PathVariable UUID documentId,
			@Valid @RequestBody ModifySuggestionRequest request
	) {
		return documentReviewService.modify(clientId, documentId, request);
	}

	private static boolean isPreviewable(String mimeType) {
		if (mimeType == null) {
			return false;
		}
		return mimeType.equalsIgnoreCase("application/pdf")
				|| mimeType.equalsIgnoreCase("image/jpeg")
				|| mimeType.equalsIgnoreCase("image/png")
				|| mimeType.equalsIgnoreCase("image/webp");
	}
}
