package com.finance.platform.finance.domain.model.chase;

import com.finance.platform.core.domain.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "client_chase_actions")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ClientChaseAction extends BaseEntity {

	@Column(name = "firm_id", nullable = false)
	private UUID firmId;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "run_id", nullable = false)
	private ClientChaseRun run;

	@Column(nullable = false, length = 20)
	@Builder.Default
	private String channel = "EMAIL";

	@Column(name = "cadence_day", nullable = false)
	private int cadenceDay;

	@Column(name = "sent_at", nullable = false)
	@Builder.Default
	private Instant sentAt = Instant.now();

	@Column(length = 255)
	private String recipient;

	@Column(nullable = false)
	@Builder.Default
	private boolean suppressed = false;

	@Column(length = 255)
	private String subject;

	@Column(name = "message_summary", columnDefinition = "TEXT")
	private String messageSummary;
}
