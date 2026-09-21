package com.finance.platform.finance.domain.model;

import com.finance.platform.core.domain.TenantAwareEntity;
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
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "practice_work_assignments")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PracticeWorkAssignment extends TenantAwareEntity {

	public enum Status {
		OPEN, IN_PROGRESS, COMPLETED, CANCELLED
	}

	@Column(name = "client_id")
	private UUID clientId;

	@Column(name = "source_type", nullable = false, length = 40)
	private String sourceType;

	@Column(name = "source_id", nullable = false)
	private UUID sourceId;

	@Column(name = "assigned_user_id")
	private UUID assignedUserId;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20)
	@Builder.Default
	private Status status = Status.OPEN;

	@Column(name = "due_date")
	private LocalDate dueDate;

	@Column(name = "completed_at")
	private Instant completedAt;
}
