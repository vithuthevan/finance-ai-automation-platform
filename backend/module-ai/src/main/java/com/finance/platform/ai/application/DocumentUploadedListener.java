package com.finance.platform.ai.application;

import com.finance.platform.finance.application.event.DocumentUploadedEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * AI document processing is handled durably via {@link com.finance.platform.core.outbox.OutboxWorker}.
 * This listener remains for observability only.
 */
@Slf4j
@Component
public class DocumentUploadedListener {

	@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
	public void onDocumentUploaded(DocumentUploadedEvent event) {
		log.debug("Document uploaded; AI queued via outbox documentId={}", event.documentId());
	}
}
