package com.finance.platform.core.outbox;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

@Slf4j
@Component
public class OutboxWorker {

	private final OutboxService outboxService;
	private final EventOutboxJpaRepository outboxRepository;
	private final List<OutboxEventHandler> handlers;
	private final TransactionTemplate transactionTemplate;
	private final String workerId;
	private final Counter processedCounter;
	private final Counter failedCounter;
	private final Timer processingTimer;

	public OutboxWorker(
			OutboxService outboxService,
			EventOutboxJpaRepository outboxRepository,
			List<OutboxEventHandler> handlers,
			TransactionTemplate transactionTemplate,
			MeterRegistry meterRegistry,
			@Value("${app.outbox.worker-id:#{T(java.util.UUID).randomUUID().toString()}}") String workerId
	) {
		this.outboxService = outboxService;
		this.outboxRepository = outboxRepository;
		this.handlers = handlers;
		this.transactionTemplate = transactionTemplate;
		this.workerId = workerId;
		this.processedCounter = meterRegistry.counter("outbox.events.processed");
		this.failedCounter = meterRegistry.counter("outbox.events.failed");
		this.processingTimer = meterRegistry.timer("outbox.events.processing");
	}

	@Scheduled(fixedDelayString = "${app.outbox.poll-interval-ms:3000}")
	public void poll() {
		Instant now = Instant.now();
		Instant lockUntil = now.plus(2, ChronoUnit.MINUTES);
		List<UUID> claimedIds = transactionTemplate.execute(status ->
				outboxService.claimBatch(workerId, 20, now, lockUntil));
		if (claimedIds == null || claimedIds.isEmpty()) {
			return;
		}
		List<EventOutbox> rows = transactionTemplate.execute(status ->
				outboxService.loadClaimed(claimedIds, workerId));
		if (rows == null) {
			return;
		}
		for (EventOutbox row : rows) {
			processRow(row);
		}
	}

	private void processRow(EventOutbox row) {
		processingTimer.record(() -> {
			try {
				for (OutboxEventHandler handler : handlers) {
					if (handler.supports(row.getEventType())) {
						handler.handle(row);
						transactionTemplate.executeWithoutResult(status -> outboxService.markProcessed(row));
						processedCounter.increment();
						log.info("outbox processed eventType={} aggregateId={}", row.getEventType(), row.getAggregateId());
						return;
					}
				}
				transactionTemplate.executeWithoutResult(status ->
						outboxService.markFailed(row, "No handler for event type " + row.getEventType()));
				failedCounter.increment();
			} catch (Exception ex) {
				log.warn("outbox processing failed eventType={} aggregateId={}: {}",
						row.getEventType(), row.getAggregateId(), ex.getMessage());
				transactionTemplate.executeWithoutResult(status -> outboxService.markFailed(row, ex.getMessage()));
				failedCounter.increment();
			}
		});
	}
}
