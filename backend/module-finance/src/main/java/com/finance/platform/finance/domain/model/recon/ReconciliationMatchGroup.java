package com.finance.platform.finance.domain.model.recon;

import com.finance.platform.core.domain.TenantAwareEntity;
import com.finance.platform.finance.domain.model.Client;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
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
@Table(name = "reconciliation_match_groups")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ReconciliationMatchGroup extends TenantAwareEntity {

	public enum Status {
		SUGGESTED, CONFIRMED, REJECTED
	}

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "client_id", nullable = false)
	private Client client;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20)
	@Builder.Default
	private Status status = Status.SUGGESTED;

	@Column(name = "match_score")
	private Integer matchScore;

	@Column(length = 10)
	private String confidence;

	@Column(columnDefinition = "TEXT")
	private String notes;

	@Column(name = "confirmed_by")
	private UUID confirmedBy;

	@Column(name = "confirmed_at")
	private Instant confirmedAt;
}
