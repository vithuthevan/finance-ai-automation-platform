package com.finance.platform.finance.application.workflow;

import com.finance.platform.finance.application.event.DocumentProcessingFailedEvent;
import com.finance.platform.finance.application.event.DocumentUploadedEvent;
import com.finance.platform.finance.application.event.PeriodReadyToCloseEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
@RequiredArgsConstructor
public class WorkflowNotificationListener {

	private final WorkflowNotificationService workflowNotificationService;

	@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
	public void onDocumentUploaded(DocumentUploadedEvent event) {
		workflowNotificationService.documentUploaded(event.firmId(), event.clientId(), event.documentId());
	}

	@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
	public void onDocumentProcessingFailed(DocumentProcessingFailedEvent event) {
		workflowNotificationService.documentProcessingFailed(event.firmId(), event.clientId(), event.documentId());
	}

	@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
	public void onPeriodReady(PeriodReadyToCloseEvent event) {
		workflowNotificationService.periodReadyToClose(event.firmId(), event.clientId(), event.periodId());
	}
}
