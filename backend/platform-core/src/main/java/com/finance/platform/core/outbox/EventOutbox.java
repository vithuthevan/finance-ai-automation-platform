package com.finance.platform.core.outbox;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "event_outbox")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class EventOutbox {

	@Id
	private UUID id;

	@Column(name = "firm_id")
	private UUID firmId;

	@Column(name = "event_type", nullable = false, length = 80)
	private String eventType;

	@Column(name = "aggregate_id")
	private UUID aggregateId;

	@Column(nullable = false, columnDefinition = "TEXT")
	private String payload;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20)
	@Builder.Default
	private Status status = Status.PENDING;

	@Column(name = "attempt_count", nullable = false)
	@Builder.Default
	private int attemptCount = 0;

	@Column(name = "last_error", length = 500)
	private String lastError;

	@Column(name = "created_at", nullable = false)
	@Builder.Default
	private Instant createdAt = Instant.now();

	@Column(name = "processed_at")
	private Instant processedAt;

	public enum Status {
		PENDING, PROCESSED, FAILED
	}
}
