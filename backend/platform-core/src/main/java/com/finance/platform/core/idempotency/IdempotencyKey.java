package com.finance.platform.core.idempotency;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "idempotency_keys", uniqueConstraints = @UniqueConstraint(columnNames = {"firm_id", "user_id", "key_hash"}))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class IdempotencyKey {

	@Id
	private UUID id;

	@Column(name = "firm_id", nullable = false)
	private UUID firmId;

	@Column(name = "user_id", nullable = false)
	private UUID userId;

	@Column(name = "key_hash", nullable = false, length = 64)
	private String keyHash;

	@Column(nullable = false, length = 10)
	private String method;

	@Column(nullable = false, length = 500)
	private String path;

	@Column(name = "request_hash", nullable = false, length = 64)
	private String requestHash;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20)
	@Builder.Default
	private Status status = Status.STARTED;

	@Column(name = "status_code")
	private Integer statusCode;

	@Column(name = "response_body", columnDefinition = "TEXT")
	private String responseBody;

	@Column(name = "created_at", nullable = false)
	@Builder.Default
	private Instant createdAt = Instant.now();

	@Column(name = "completed_at")
	private Instant completedAt;

	public enum Status {
		STARTED, COMPLETED
	}
}
