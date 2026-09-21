package com.finance.platform.finance.domain.model.invoicing;

import com.finance.platform.core.domain.TenantAwareEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
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
@Table(name = "ar_payments")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ArPayment extends TenantAwareEntity {

	public enum Status {
		RECEIVED, PARTIALLY_ALLOCATED, ALLOCATED, REVERSED
	}

	public enum Source {
		MANUAL, BANK_IMPORT, OTHER
	}

	@Column(name = "customer_id")
	private UUID customerId;

	@Column(name = "payment_date", nullable = false)
	private LocalDate paymentDate;

	@Column(nullable = false, precision = 19, scale = 4)
	private BigDecimal amount;

	@Column(length = 120)
	private String reference;

	@Column(name = "unallocated_amount", nullable = false, precision = 19, scale = 4)
	@Builder.Default
	private BigDecimal unallocatedAmount = BigDecimal.ZERO;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20)
	@Builder.Default
	private Status status = Status.RECEIVED;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 40)
	@Builder.Default
	private Source source = Source.MANUAL;

	@Column(name = "reversed_at")
	private Instant reversedAt;

	@Column(name = "reversal_reason", length = 500)
	private String reversalReason;

	@Column(name = "reversed_by")
	private UUID reversedBy;

	@Column(name = "bank_transaction_id")
	private UUID bankTransactionId;

	@Version
	@Column(name = "row_version", nullable = false)
	@Builder.Default
	private int rowVersion = 0;
}
