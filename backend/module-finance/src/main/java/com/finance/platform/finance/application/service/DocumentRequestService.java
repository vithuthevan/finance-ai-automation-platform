package com.finance.platform.finance.application.service;

import com.finance.platform.core.audit.AuditAction;
import com.finance.platform.core.audit.AuditEvent;
import com.finance.platform.core.audit.AuditLogger;
import com.finance.platform.core.audit.AuditResourceType;
import com.finance.platform.core.exception.ResourceNotFoundException;
import com.finance.platform.core.exception.ValidationException;
import com.finance.platform.core.notification.Notification;
import com.finance.platform.core.notification.NotificationJpaRepository;
import com.finance.platform.finance.domain.model.Client;
import com.finance.platform.finance.domain.model.DocumentRequest;
import com.finance.platform.finance.domain.model.Receipt;
import com.finance.platform.finance.infrastructure.persistence.DocumentRequestJpaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class DocumentRequestService {

	private final DocumentRequestJpaRepository requestRepository;
	private final ClientAccessService clientAccessService;
	private final NotificationJpaRepository notificationRepository;
	private final AuditLogger auditLogger;

	@Transactional(readOnly = true)
	public List<DocumentRequest> list(UUID clientId) {
		clientAccessService.requireReadAccess(clientId);
		return requestRepository.findByClient_IdOrderByCreatedAtDesc(clientId);
	}

	@Transactional
	public DocumentRequest create(UUID clientId, String description, Receipt.DocumentType type, LocalDate dueDate, UUID assigneeUserId) {
		Client client = clientAccessService.requireWriteAccess(clientId);
		if (description == null || description.isBlank()) {
			throw new ValidationException("description", "Description is required");
		}
		DocumentRequest request = DocumentRequest.builder()
				.client(client)
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
				.afterState(java.util.Map.of("description", saved.getDescription()))
				.build());
		return saved;
	}

	@Transactional
	public DocumentRequest attachUpload(UUID clientId, UUID requestId, UUID documentId) {
		clientAccessService.requireUploadAccess(clientId);
		DocumentRequest request = requestRepository.findByIdAndClient_Id(requestId, clientId)
				.orElseThrow(() -> new ResourceNotFoundException("DocumentRequest", requestId));
		request.setUploadedDocumentId(documentId);
		request.setStatus(DocumentRequest.RequestStatus.UPLOADED);
		return requestRepository.save(request);
	}

	@Transactional
	public DocumentRequest complete(UUID clientId, UUID requestId) {
		clientAccessService.requireWriteAccess(clientId);
		DocumentRequest request = requestRepository.findByIdAndClient_Id(requestId, clientId)
				.orElseThrow(() -> new ResourceNotFoundException("DocumentRequest", requestId));
		request.setStatus(DocumentRequest.RequestStatus.COMPLETED);
		request.setCompletedAt(Instant.now());
		return requestRepository.save(request);
	}

	@Transactional
	public DocumentRequest cancel(UUID clientId, UUID requestId) {
		clientAccessService.requireWriteAccess(clientId);
		DocumentRequest request = requestRepository.findByIdAndClient_Id(requestId, clientId)
				.orElseThrow(() -> new ResourceNotFoundException("DocumentRequest", requestId));
		request.setStatus(DocumentRequest.RequestStatus.CANCELLED);
		return requestRepository.save(request);
	}
}
