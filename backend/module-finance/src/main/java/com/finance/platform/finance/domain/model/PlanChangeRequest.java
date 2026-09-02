package com.finance.platform.finance.domain.model;

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
@Table(name = "plan_change_requests")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PlanChangeRequest extends BaseEntity {

	@Column(nullable = false)
	private UUID firmId;

	@Column(nullable = false)
	private UUID requestedBy;

	@Column(nullable = false, length = 40)
	private String currentPlanCode;

	@Column(nullable = false, length = 40)
	private String requestedPlanCode;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20)
	@Builder.Default
	private RequestStatus status = RequestStatus.OPEN;

	@Column(columnDefinition = "TEXT")
	private String note;

	private Instant resolvedAt;

	public enum RequestStatus {
		OPEN, APPROVED, REJECTED, CANCELLED
	}
}
