package com.finance.platform.core.outbox;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class OutboxService {

	public static final String DOCUMENT_UPLOADED = "DOCUMENT_UPLOADED";

	private final EventOutboxJpaRepository outboxRepository;
	private final ObjectMapper objectMapper;

	@Transactional
	public void append(String eventType, UUID firmId, UUID aggregateId, Object payload) {
		EventOutbox row = EventOutbox.builder()
				.id(UUID.randomUUID())
				.firmId(firmId)
				.eventType(eventType)
				.aggregateId(aggregateId)
				.payload(writeJson(payload))
				.status(EventOutbox.Status.PENDING)
				.createdAt(Instant.now())
				.build();
		outboxRepository.save(row);
	}

	@Transactional
	public void markProcessed(String eventType, UUID aggregateId) {
		outboxRepository.findFirstByEventTypeAndAggregateIdAndStatus(
						eventType, aggregateId, EventOutbox.Status.PENDING)
				.ifPresent(row -> {
					row.setStatus(EventOutbox.Status.PROCESSED);
					row.setProcessedAt(Instant.now());
					outboxRepository.save(row);
				});
	}

	@Transactional
	public void markFailed(EventOutbox row, String error) {
		row.setAttemptCount(row.getAttemptCount() + 1);
		row.setLastError(truncate(error, 500));
		if (row.getAttemptCount() >= 10) {
			row.setStatus(EventOutbox.Status.FAILED);
		}
		outboxRepository.save(row);
	}

	public ListDue pendingDue(Instant createdBefore) {
		return new ListDue(outboxRepository.findPendingDue(createdBefore));
	}

	private String writeJson(Object payload) {
		try {
			return objectMapper.writeValueAsString(payload);
		} catch (JsonProcessingException ex) {
			throw new IllegalStateException("Unable to serialize outbox payload", ex);
		}
	}

	private static String truncate(String value, int max) {
		if (value == null) {
			return null;
		}
		return value.length() <= max ? value : value.substring(0, max);
	}

	public record ListDue(java.util.List<EventOutbox> rows) {
	}
}
