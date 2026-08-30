package com.finance.platform.ai.application;

import com.finance.platform.finance.application.event.DocumentUploadedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;

@Slf4j
@Component
@RequiredArgsConstructor
public class DocumentUploadedListener {

	private final DocumentAiProcessor documentAiProcessor;

	@Async
	@TransactionalEventListener
	public void onDocumentUploaded(DocumentUploadedEvent event) {
		try {
			documentAiProcessor.process(event.documentId());
		} catch (Exception ex) {
			log.warn("Async AI processing failed for document {}: {}", event.documentId(), ex.getMessage());
		}
	}
}
