package com.finance.platform.finance.domain.model.chase;

import com.finance.platform.core.domain.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "client_chase_runs")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ClientChaseRun extends BaseEntity {

	public enum Status {
		ACTIVE, COMPLETED, SUPPRESSED, FAILED
	}

	@Column(name = "firm_id", nullable = false)
	private UUID firmId;

	@Column(name = "client_id", nullable = false)
	private UUID clientId;

	@Column(name = "policy_id", nullable = false)
	private UUID policyId;

	@Column(name = "source_type", nullable = false, length = 40)
	private String sourceType;

	@Column(name = "source_id", nullable = false)
	private UUID sourceId;

	@Column(name = "started_at", nullable = false)
	@Builder.Default
	private Instant startedAt = Instant.now();

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20)
	@Builder.Default
	private Status status = Status.ACTIVE;
}
