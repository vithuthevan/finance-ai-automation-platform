package com.finance.platform.finance.application.workflow;

import com.finance.platform.core.notification.EmailTemplateService;
import com.finance.platform.core.notification.NotificationCommand;
import com.finance.platform.core.notification.NotificationDispatcher;
import com.finance.platform.core.notification.NotificationType;
import com.finance.platform.finance.domain.model.AccountingPeriod;
import com.finance.platform.finance.domain.model.Client;
import com.finance.platform.finance.domain.model.DocumentRequest;
import com.finance.platform.finance.domain.model.Receipt;
import com.finance.platform.finance.infrastructure.persistence.AccountingPeriodJpaRepository;
import com.finance.platform.finance.infrastructure.persistence.ClientJpaRepository;
import com.finance.platform.finance.infrastructure.persistence.ReceiptJpaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class WorkflowNotificationService {

	private final NotificationDispatcher notificationDispatcher;
	private final NotificationRecipientResolver recipientResolver;
	private final EmailTemplateService emailTemplateService;
	private final ClientJpaRepository clientRepository;
	private final ReceiptJpaRepository receiptRepository;
	private final AccountingPeriodJpaRepository periodRepository;

	public void documentUploaded(UUID firmId, UUID clientId, UUID documentId) {
		Client client = clientRepository.findByIdAndFirmIdAndDeletedAtIsNull(clientId, firmId).orElse(null);
		if (client == null) {
			return;
		}
		Set<UUID> recipients = recipientResolver.assignedAccountants(firmId, clientId);
		if (recipients.isEmpty()) {
			return;
		}
		String message = emailTemplateService.documentReviewNeeded(client.getName(), "A new document was uploaded.");
		notificationDispatcher.dispatch(new NotificationCommand(
				firmId,
				recipients,
				clientId,
				NotificationType.DOCUMENT_UPLOADED,
				"New document uploaded",
				message,
				"DOCUMENT",
				documentId,
				"/app/documents/" + clientId + "/" + documentId,
				"document-uploaded:" + documentId,
				false
		));
	}

	public void documentNeedsReview(UUID firmId, UUID clientId, UUID documentId, String description) {
		Client client = clientRepository.findByIdAndFirmIdAndDeletedAtIsNull(clientId, firmId).orElse(null);
		if (client == null) {
			return;
		}
		Set<UUID> recipients = recipientResolver.assignedAccountants(firmId, clientId);
		if (recipients.isEmpty()) {
			return;
		}
		String message = emailTemplateService.documentReviewNeeded(client.getName(), description);
		notificationDispatcher.dispatch(new NotificationCommand(
				firmId,
				recipients,
				clientId,
				NotificationType.DOCUMENT_NEEDS_REVIEW,
				"Document needs review",
				message,
				"DOCUMENT",
				documentId,
				"/app/documents/" + clientId + "/" + documentId,
				"document-review:" + documentId,
				false
		));
	}

	public void documentProcessingFailed(UUID firmId, UUID clientId, UUID documentId) {
		Client client = clientRepository.findByIdAndFirmIdAndDeletedAtIsNull(clientId, firmId).orElse(null);
		if (client == null) {
			return;
		}
		Set<UUID> recipients = recipientResolver.assignedAccountants(firmId, clientId);
		if (recipients.isEmpty()) {
			return;
		}
		Receipt receipt = receiptRepository.findById(documentId).orElse(null);
		String detail = receipt != null && receipt.getDescription() != null ? receipt.getDescription() : "Manual review required.";
		String message = emailTemplateService.documentReviewNeeded(client.getName(), detail);
		notificationDispatcher.dispatch(new NotificationCommand(
				firmId,
				recipients,
				clientId,
				NotificationType.DOCUMENT_PROCESSING_FAILED,
				"Document processing failed",
				message,
				"DOCUMENT",
				documentId,
				"/app/documents/" + clientId + "/" + documentId,
				"document-failed:" + documentId,
				false
		));
	}

	public void documentRequestCreated(DocumentRequest request) {
		Client client = request.getClient();
		Set<UUID> recipients = recipientResolver.resolveDocumentRequestRecipients(
				client.getId(), request.getAssigneeUserId());
		if (recipients.isEmpty()) {
			return;
		}
		String title = request.getTitle() != null && !request.getTitle().isBlank()
				? request.getTitle()
				: request.getDescription();
		String due = request.getDueDate() == null ? null : request.getDueDate().toString();
		String message = emailTemplateService.documentRequested(client.getName(), title, due);
		notificationDispatcher.dispatch(new NotificationCommand(
				request.getFirmId(),
				recipients,
				client.getId(),
				NotificationType.DOCUMENT_REQUEST_CREATED,
				"Document requested",
				message,
				"DOCUMENT_REQUEST",
				request.getId(),
				"/app/owner",
				"document-request:" + request.getId(),
				true
		));
	}

	public void documentRequestUploaded(DocumentRequest request) {
		Client client = request.getClient();
		UUID accountantId = request.getRequestedBy() != null ? request.getRequestedBy().getId() : null;
		Set<UUID> recipients = accountantId == null
				? recipientResolver.assignedAccountants(request.getFirmId(), client.getId())
				: Set.of(accountantId);
		if (recipients.isEmpty()) {
			return;
		}
		String message = emailTemplateService.documentUploaded(client.getName(), request.getDescription());
		notificationDispatcher.dispatch(new NotificationCommand(
				request.getFirmId(),
				recipients,
				client.getId(),
				NotificationType.DOCUMENT_REQUEST_UPLOADED,
				"Requested document uploaded",
				message,
				"DOCUMENT_REQUEST",
				request.getId(),
				"/app/documents?clientId=" + client.getId(),
				"document-request-uploaded:" + request.getId(),
				true
		));
	}

	public void documentRequestOverdue(DocumentRequest request) {
		Client client = request.getClient();
		Set<UUID> recipients = recipientResolver.resolveDocumentRequestRecipients(
				client.getId(), request.getAssigneeUserId());
		if (recipients.isEmpty()) {
			return;
		}
		String title = request.getTitle() != null && !request.getTitle().isBlank()
				? request.getTitle()
				: request.getDescription();
		notificationDispatcher.dispatch(new NotificationCommand(
				request.getFirmId(),
				recipients,
				client.getId(),
				NotificationType.DOCUMENT_REQUEST_OVERDUE,
				"Document request overdue",
				"Overdue: " + title,
				"DOCUMENT_REQUEST",
				request.getId(),
				"/app/owner",
				"document-request-overdue:" + request.getId(),
				true
		));
	}

	public void bankImportCompleted(UUID firmId, UUID clientId, UUID importId) {
		Client client = clientRepository.findByIdAndFirmIdAndDeletedAtIsNull(clientId, firmId).orElse(null);
		if (client == null) {
			return;
		}
		Set<UUID> recipients = recipientResolver.assignedAccountants(firmId, clientId);
		if (recipients.isEmpty()) {
			return;
		}
		notificationDispatcher.dispatch(new NotificationCommand(
				firmId,
				recipients,
				clientId,
				NotificationType.BANK_IMPORT_COMPLETED,
				"Bank import completed",
				"A bank statement import for " + client.getName() + " is ready for reconciliation.",
				"BANK_IMPORT",
				importId,
				"/app/banking?clientId=" + clientId,
				"bank-import:" + importId,
				false
		));
	}

	public void periodReadyToClose(UUID firmId, UUID clientId, UUID periodId) {
		Client client = clientRepository.findByIdAndFirmIdAndDeletedAtIsNull(clientId, firmId).orElse(null);
		AccountingPeriod period = periodRepository.findById(periodId).orElse(null);
		if (client == null || period == null) {
			return;
		}
		Set<UUID> recipients = recipientResolver.assignedAccountants(firmId, clientId);
		if (recipients.isEmpty()) {
			return;
		}
		String label = periodLabel(period);
		String message = emailTemplateService.periodReady(client.getName(), label);
		notificationDispatcher.dispatch(new NotificationCommand(
				firmId,
				recipients,
				clientId,
				NotificationType.PERIOD_READY_TO_CLOSE,
				"Period ready to close",
				message,
				"PERIOD",
				periodId,
				"/app/close/" + clientId + "/" + periodId,
				"period-ready:" + periodId,
				true
		));
	}

	public void periodClosed(UUID firmId, UUID clientId, UUID periodId) {
		Client client = clientRepository.findByIdAndFirmIdAndDeletedAtIsNull(clientId, firmId).orElse(null);
		AccountingPeriod period = periodRepository.findById(periodId).orElse(null);
		if (client == null || period == null) {
			return;
		}
		Set<UUID> owners = recipientResolver.businessOwners(clientId);
		String label = periodLabel(period);
		if (!owners.isEmpty()) {
			String message = emailTemplateService.periodClosed(client.getName(), label);
			notificationDispatcher.dispatch(new NotificationCommand(
					firmId,
					owners,
					clientId,
					NotificationType.PERIOD_CLOSED,
					"Bookkeeping period closed",
					message,
					"PERIOD",
					periodId,
					"/app/owner",
					"period-closed-owner:" + periodId,
					true
			));
		}
		Set<UUID> auditors = recipientResolver.auditors(firmId, clientId);
		if (!auditors.isEmpty()) {
			notificationDispatcher.dispatch(new NotificationCommand(
					firmId,
					auditors,
					clientId,
					NotificationType.PERIOD_CLOSED,
					"Period closed for review",
					client.getName() + " — " + label + " is closed.",
					"PERIOD",
					periodId,
					"/app/close/" + clientId + "/" + periodId,
					"period-closed-auditor:" + periodId,
					false
			));
		}
	}

	private static String periodLabel(AccountingPeriod period) {
		YearMonth ym = YearMonth.of(period.getPeriodYear(), period.getPeriodMonth());
		return ym.format(DateTimeFormatter.ofPattern("MMMM yyyy", Locale.ENGLISH));
	}
}
