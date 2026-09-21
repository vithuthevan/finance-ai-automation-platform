package com.finance.platform.core.outbox;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class OutboxService {

	public static final String DOCUMENT_AI_PROCESS = "DOCUMENT_AI_PROCESS";
	public static final String NOTIFICATION_DISPATCH = "NOTIFICATION_DISPATCH";

	private static final int MAX_ATTEMPTS = 10;

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
				.nextAttemptAt(Instant.now())
				.build();
		outboxRepository.save(row);
	}

	@Transactional
	public List<UUID> claimBatch(String workerId, int batchSize, Instant now, Instant lockUntil) {
		return outboxRepository.claimBatch(now, lockUntil, workerId, batchSize);
	}

	@Transactional(readOnly = true)
	public List<EventOutbox> loadClaimed(List<UUID> ids, String workerId) {
		return outboxRepository.findAllById(ids).stream()
				.filter(row -> workerId.equals(row.getLockedBy()))
				.toList();
	}

	@Transactional
	public void markProcessed(EventOutbox row) {
		row.setStatus(EventOutbox.Status.PROCESSED);
		row.setProcessedAt(Instant.now());
		row.setLockedUntil(null);
		row.setLockedBy(null);
		row.setLastError(null);
		outboxRepository.save(row);
	}

	@Transactional
	public void markFailed(EventOutbox row, String error) {
		row.setAttemptCount(row.getAttemptCount() + 1);
		row.setLastError(truncate(error, 500));
		row.setLockedUntil(null);
		row.setLockedBy(null);
		if (row.getAttemptCount() >= MAX_ATTEMPTS) {
			row.setStatus(EventOutbox.Status.FAILED);
		} else {
			long backoffSeconds = Math.min(3600, (long) Math.pow(2, Math.min(row.getAttemptCount(), 8)));
			row.setNextAttemptAt(Instant.now().plus(backoffSeconds, ChronoUnit.SECONDS));
		}
		outboxRepository.save(row);
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
}
