package com.finance.platform.finance.application.service;

import com.finance.platform.core.audit.AuditAction;
import com.finance.platform.core.audit.AuditEvent;
import com.finance.platform.core.audit.AuditLogger;
import com.finance.platform.core.audit.AuditResourceType;
import com.finance.platform.core.dto.PageRequests;
import com.finance.platform.core.dto.PageResponse;
import com.finance.platform.core.exception.BusinessException;
import com.finance.platform.core.exception.ErrorCodes;
import com.finance.platform.core.exception.ResourceNotFoundException;
import com.finance.platform.core.exception.ValidationException;
import com.finance.platform.core.notification.Notification;
import com.finance.platform.core.notification.NotificationJpaRepository;
import com.finance.platform.finance.application.dto.DocumentResponse;
import com.finance.platform.finance.domain.model.AccountingPeriod;
import com.finance.platform.finance.domain.model.Client;
import com.finance.platform.finance.domain.model.DocumentRequest;
import com.finance.platform.finance.domain.model.Receipt;
import com.finance.platform.finance.infrastructure.persistence.AccountingPeriodJpaRepository;
import com.finance.platform.finance.infrastructure.persistence.DocumentRequestJpaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class DocumentRequestService {

	private final DocumentRequestJpaRepository requestRepository;
	private final AccountingPeriodJpaRepository periodRepository;
	private final ClientAccessService clientAccessService;
	private final DocumentService documentService;
	private final NotificationJpaRepository notificationRepository;
	private final AuditLogger auditLogger;

	@Transactional(readOnly = true)
	public PageResponse<DocumentRequest> list(
			UUID clientId,
			DocumentRequest.RequestStatus status,
			UUID periodId,
			int page,
			int size
	) {
		clientAccessService.requireReadAccess(clientId);
		Page<DocumentRequest> results = requestRepository.search(
				clientId,
				status,
				periodId,
				PageRequests.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt")));
		results.getContent().forEach(request -> {
			request.getClient().getId();
			if (request.getPeriod() != null) {
				request.getPeriod().getId();
			}
		});
		return new PageResponse<>(results.getContent(), results.getNumber(), results.getSize(), results.getTotalElements());
	}

	@Transactional
	public DocumentRequest create(
			UUID clientId,
			String description,
			Receipt.DocumentType type,
			LocalDate dueDate,
			UUID assigneeUserId,
			UUID periodId
	) {
		Client client = clientAccessService.requireWriteAccess(clientId);
		if (description == null || description.isBlank()) {
			throw new ValidationException("description", "Description is required");
		}
		AccountingPeriod period = null;
		if (periodId != null) {
			period = periodRepository.findByIdAndClient_Id(periodId, clientId)
					.orElseThrow(() -> new ResourceNotFoundException("Accounting period", periodId));
		}
		DocumentRequest request = DocumentRequest.builder()
				.client(client)
				.period(period)
				.requestedBy(clientAccessService.requireCurrentUserEntity())
				.assigneeUserId(assigneeUserId)
				.description(description.trim())
				.documentType(type != null ? type : Receipt.DocumentType.OTHER)
				.dueDate(dueDate)
				.status(DocumentRequest.RequestStatus.OPEN)
				.build();
		request.setFirmId(client.getFirmId());
		DocumentRequest saved = requestRepository.save(request);
		if (assigneeUserId != null) {
			notificationRepository.save(Notification.builder()
					.firmId(client.getFirmId())
					.userId(assigneeUserId)
					.clientId(clientId)
					.type("DOCUMENT_REQUESTED")
					.title("Document requested")
					.message(saved.getDescription())
					.build());
		}
		auditLogger.record(AuditEvent.fromTenant()
				.firmId(client.getFirmId())
				.action(AuditAction.DOCUMENT_REQUESTED)
				.resourceType(AuditResourceType.DOCUMENT_REQUEST)
				.resourceId(saved.getId())
				.clientId(clientId)
				.afterState(Map.of(
						"description", saved.getDescription(),
						"periodId", periodId == null ? "" : periodId.toString()
				))
				.build());
		return saved;
	}

	@Transactional
	public DocumentRequest attachUpload(UUID clientId, UUID requestId, UUID documentId) {
		clientAccessService.requireUploadAccess(clientId);
		DocumentRequest request = requireOpenOrUploaded(clientId, requestId);
		documentService.requireDocument(clientId, documentId);
		request.setUploadedDocumentId(documentId);
		request.setStatus(DocumentRequest.RequestStatus.UPLOADED);
		DocumentRequest saved = requestRepository.save(request);
		auditLogger.record(AuditEvent.fromTenant()
				.firmId(saved.getFirmId())
				.action(AuditAction.DOCUMENT_REQUEST_UPLOADED)
				.resourceType(AuditResourceType.DOCUMENT_REQUEST)
				.resourceId(saved.getId())
				.clientId(clientId)
				.afterState(Map.of("documentId", documentId.toString()))
				.build());
		return saved;
	}

	@Transactional
	public DocumentRequest uploadAgainstRequest(
			UUID clientId,
			UUID requestId,
			byte[] content,
			String originalFilename,
			String declaredMimeType
	) {
		DocumentRequest request = requireOpenOrUploaded(clientId, requestId);
		Receipt.DocumentType type = request.getDocumentType() == null ? Receipt.DocumentType.OTHER : request.getDocumentType();
		DocumentResponse uploaded = documentService.upload(
				clientId, content, originalFilename, declaredMimeType, type, request.getDescription(), false);
		return attachUpload(clientId, requestId, uploaded.id());
	}

	@Transactional
	public DocumentRequest complete(UUID clientId, UUID requestId) {
		clientAccessService.requireWriteAccess(clientId);
		DocumentRequest request = requireRequest(clientId, requestId);
		if (request.getStatus() == DocumentRequest.RequestStatus.COMPLETED) {
			throw new BusinessException(ErrorCodes.DOCUMENT_REQUEST_ALREADY_COMPLETED, "Document request is already completed");
		}
		if (request.getStatus() == DocumentRequest.RequestStatus.CANCELLED) {
			throw new BusinessException(ErrorCodes.INVALID_STATUS_TRANSITION, "A cancelled request cannot be completed");
		}
		request.setStatus(DocumentRequest.RequestStatus.COMPLETED);
		request.setCompletedAt(Instant.now());
		DocumentRequest saved = requestRepository.save(request);
		auditLogger.record(AuditEvent.fromTenant()
				.firmId(saved.getFirmId())
				.action(AuditAction.DOCUMENT_REQUEST_COMPLETED)
				.resourceType(AuditResourceType.DOCUMENT_REQUEST)
				.resourceId(saved.getId())
				.clientId(clientId)
				.afterState(Map.of("status", "COMPLETED"))
				.build());
		return saved;
	}

	@Transactional
	public DocumentRequest cancel(UUID clientId, UUID requestId) {
		clientAccessService.requireWriteAccess(clientId);
		DocumentRequest request = requireRequest(clientId, requestId);
		if (request.getStatus() == DocumentRequest.RequestStatus.COMPLETED) {
			throw new BusinessException(ErrorCodes.DOCUMENT_REQUEST_ALREADY_COMPLETED, "A completed request cannot be cancelled");
		}
		if (request.getStatus() == DocumentRequest.RequestStatus.CANCELLED) {
			return request;
		}
		request.setStatus(DocumentRequest.RequestStatus.CANCELLED);
		DocumentRequest saved = requestRepository.save(request);
		auditLogger.record(AuditEvent.fromTenant()
				.firmId(saved.getFirmId())
				.action(AuditAction.DOCUMENT_REQUEST_CANCELLED)
				.resourceType(AuditResourceType.DOCUMENT_REQUEST)
				.resourceId(saved.getId())
				.clientId(clientId)
				.afterState(Map.of("status", "CANCELLED"))
				.build());
		return saved;
	}

	private DocumentRequest requireRequest(UUID clientId, UUID requestId) {
		return requestRepository.findByIdAndClient_Id(requestId, clientId)
				.orElseThrow(() -> new ResourceNotFoundException("Document request", requestId));
	}

	private DocumentRequest requireOpenOrUploaded(UUID clientId, UUID requestId) {
		DocumentRequest request = requireRequest(clientId, requestId);
		if (request.getStatus() == DocumentRequest.RequestStatus.COMPLETED) {
			throw new BusinessException(ErrorCodes.DOCUMENT_REQUEST_ALREADY_COMPLETED, "Document request is already completed");
		}
		if (request.getStatus() == DocumentRequest.RequestStatus.CANCELLED) {
			throw new BusinessException(ErrorCodes.INVALID_STATUS_TRANSITION, "A cancelled request cannot receive uploads");
		}
		return request;
	}
}
