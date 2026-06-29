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

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "expenses")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Expense extends TenantAwareEntity {

	@Column(nullable = false)
	private UUID clientId;

	@Column(nullable = false)
	private UUID categoryId;

	@Column(nullable = false)
	private LocalDate transactionDate;

	@Column(nullable = false, precision = 19, scale = 4)
	private BigDecimal amount;

	@Column(nullable = false, length = 3)
	@Builder.Default
	private String currencyCode = "LKR";

	@Column(nullable = false, length = 200)
	private String vendorName;

	@Column(columnDefinition = "TEXT")
	private String description;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20)
	@Builder.Default
	private TransactionStatus status = TransactionStatus.DRAFT;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20)
	@Builder.Default
	private TransactionSource source = TransactionSource.MANUAL;

	private UUID primaryReceiptId;
	private UUID createdByUserId;
	private UUID approvedByUserId;
	private Instant approvedAt;
}
