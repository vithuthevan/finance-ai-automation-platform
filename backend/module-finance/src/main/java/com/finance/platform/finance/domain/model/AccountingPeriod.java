package com.finance.platform.finance.domain.model;

import com.finance.platform.auth.domain.model.User;
import com.finance.platform.core.domain.TenantAwareEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.persistence.Version;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.time.LocalDate;

@Entity
@Table(name = "accounting_periods", uniqueConstraints = @UniqueConstraint(columnNames = {"firm_id", "client_id", "period_year", "period_month"}))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AccountingPeriod extends TenantAwareEntity {

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "client_id", nullable = false)
	private Client client;

	@Column(name = "period_year", nullable = false)
	private int periodYear;

	@Column(name = "period_month", nullable = false)
	private int periodMonth;

	@Column(name = "start_date", nullable = false)
	private LocalDate startDate;

	@Column(name = "end_date", nullable = false)
	private LocalDate endDate;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20)
	@Builder.Default
	private PeriodStatus status = PeriodStatus.OPEN;

	@Column(name = "close_note", columnDefinition = "TEXT")
	private String closeNote;

	private Instant reviewStartedAt;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "review_started_by")
	private User reviewStartedBy;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "closed_by")
	private User closedBy;

	private Instant closedAt;

	@Column(columnDefinition = "TEXT")
	private String reopenReason;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "reopened_by")
	private User reopenedBy;

	private Instant reopenedAt;

	@Version
	@Column(name = "row_version", nullable = false)
	@Builder.Default
	private Integer rowVersion = 0;

	public enum PeriodStatus {
		OPEN, IN_REVIEW, READY_TO_CLOSE, CLOSED, REOPENED
	}

	public boolean isClosed() {
		return status == PeriodStatus.CLOSED;
	}
}
