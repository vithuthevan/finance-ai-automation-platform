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

	@Column(nullable = false, length = 40)
	@Builder.Default
	private String planCode = "STANDARD";

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20)
	@Builder.Default
	private SubscriptionStatus status = SubscriptionStatus.ACTIVE;

	@Column(nullable = false)
	@Builder.Default
	private int maxClients = 50;

	@Column(nullable = false)
	@Builder.Default
	private int maxUsers = 15;

	@Column(nullable = false)
	@Builder.Default
	private int monthlyDocuments = 2000;

	@Column(nullable = false)
	@Builder.Default
	private int aiMonthlyAllowance = 1000;

	@Column(nullable = false)
	@Builder.Default
	private long storageBytes = 10L * 1024 * 1024 * 1024;

	public enum SubscriptionStatus {
		TRIAL, ACTIVE, PAST_DUE, CANCELLED
	}
}
