package com.finance.platform.finance.domain.model;

import com.finance.platform.core.domain.BaseEntity;
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
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "firm_subscriptions")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class FirmSubscription extends BaseEntity {

	@Column(nullable = false, unique = true)
	private UUID firmId;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "plan_id")
	private SubscriptionPlan plan;

	@Column(nullable = false, length = 40)
	@Builder.Default
	private String planCode = "STARTER";

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20)
	@Builder.Default
	private SubscriptionStatus status = SubscriptionStatus.TRIAL;

	@Column(nullable = false)
	@Builder.Default
	private int maxClients = 10;

	@Column(nullable = false)
	@Builder.Default
	private int maxUsers = 3;

	@Column(nullable = false)
	@Builder.Default
	private int monthlyDocuments = 500;

	@Column(nullable = false)
	@Builder.Default
	private int aiMonthlyAllowance = 100;

	@Column(nullable = false)
	@Builder.Default
	private long storageBytes = 2L * 1024 * 1024 * 1024;

	private Instant startedAt;

	private LocalDate currentPeriodStart;

	private LocalDate currentPeriodEnd;

	private Instant trialEndsAt;

	@Column(nullable = false)
	@Builder.Default
	private boolean cancelAtPeriodEnd = false;

	private Instant cancelledAt;

	private Instant suspendedAt;

	public enum SubscriptionStatus {
		TRIAL, ACTIVE, PAST_DUE, SUSPENDED, CANCELLED
	}

	public boolean allowsWrite() {
		return status == SubscriptionStatus.TRIAL
				|| status == SubscriptionStatus.ACTIVE
				|| status == SubscriptionStatus.PAST_DUE;
	}
}
