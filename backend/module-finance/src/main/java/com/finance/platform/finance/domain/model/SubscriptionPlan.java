package com.finance.platform.finance.domain.model;

import com.finance.platform.core.domain.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "subscription_plans")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SubscriptionPlan extends BaseEntity {

	@Column(nullable = false, unique = true, length = 40)
	private String code;

	@Column(nullable = false, length = 120)
	private String name;

	@Column(columnDefinition = "TEXT")
	private String description;

	@Column(nullable = false)
	@Builder.Default
	private boolean active = true;

	@Column(nullable = false)
	private int maxClients;

	@Column(nullable = false)
	private int maxUsers;

	@Column(nullable = false)
	private int monthlyDocumentLimit;

	@Column(nullable = false)
	private int monthlyAiLimit;

	@Column(nullable = false)
	private long storageLimitBytes;

	@Column(columnDefinition = "TEXT")
	private String features;
}
