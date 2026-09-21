package com.finance.platform.ai.application;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.finance.platform.core.outbox.EventOutbox;
import com.finance.platform.core.outbox.OutboxEventHandler;
import com.finance.platform.core.outbox.OutboxService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
@RequiredArgsConstructor
public class DocumentAiOutboxHandler implements OutboxEventHandler {

	private final DocumentAiProcessor documentAiProcessor;
	private final ObjectMapper objectMapper;

	@Override
	public boolean supports(String eventType) {
		return OutboxService.DOCUMENT_AI_PROCESS.equals(eventType);
	}

	@Override
	public void handle(EventOutbox row) {
		UUID receiptId = row.getAggregateId();
		if (receiptId == null) {
			receiptId = readDocumentId(row.getPayload());
		}
		if (receiptId == null) {
			throw new IllegalStateException("Missing document id in outbox payload");
		}
		documentAiProcessor.process(receiptId);
	}

	private UUID readDocumentId(String payload) {
		try {
			JsonNode node = objectMapper.readTree(payload);
			if (node.hasNonNull("documentId")) {
				return UUID.fromString(node.get("documentId").asText());
			}
		} catch (Exception ignored) {
			// fall through
		}
		return null;
	}
}
