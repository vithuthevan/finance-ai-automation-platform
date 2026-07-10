package com.finance.platform.finance.domain.model;

import com.finance.platform.auth.domain.model.User;
import com.finance.platform.core.domain.TenantAwareEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

@Entity
@Table(name = "income", indexes = {
		@Index(columnList = "client_id, transaction_date"),
		@Index(columnList = "client_id, status")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Income extends TenantAwareEntity {

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "client_id", nullable = false)
	private Client client;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "category_id", nullable = false)
	private Category category;

	@Column(nullable = false)
	private LocalDate transactionDate;

	@Column(nullable = false, precision = 19, scale = 4)
	private BigDecimal amount;

	@Column(nullable = false, length = 3)
	@Builder.Default
	private String currencyCode = "LKR";

	@Column(nullable = false, length = 200)
	private String customerName;

	@Column(columnDefinition = "TEXT")
	private String description;

	@Column(precision = 19, scale = 4)
	private BigDecimal taxAmount;

	@Column(length = 100)
	private String referenceNo;

	@Enumerated(EnumType.STRING)
	@Column(name = "payment_method", length = 20)
	private PaymentMethod paymentMethod;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20)
	@Builder.Default
	private TransactionStatus status = TransactionStatus.DRAFT;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20)
	@Builder.Default
	private TransactionSource source = TransactionSource.MANUAL;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "primary_receipt_id")
	private Receipt primaryReceipt;

	@ManyToMany
	@JoinTable(
			name = "income_receipts",
			joinColumns = @JoinColumn(name = "income_id"),
			inverseJoinColumns = @JoinColumn(name = "receipt_id")
	)
	@Builder.Default
	private Set<Receipt> receipts = new HashSet<>();

	@Column(name = "extraction_suggestion_id")
	private UUID extractionSuggestionId;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "created_by_user_id", nullable = false)
	private User createdByUser;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "approved_by_user_id")
	private User approvedByUser;

	private Instant approvedAt;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "voided_by_user_id")
	private User voidedByUser;

	private Instant voidedAt;

	@Column(columnDefinition = "TEXT")
	private String voidReason;

	public void approve(User approver) {
		if (status != TransactionStatus.DRAFT) {
			throw new IllegalStateException("Only DRAFT income can be approved");
		}
		this.status = TransactionStatus.APPROVED;
		this.approvedByUser = approver;
		this.approvedAt = Instant.now();
	}

	public void voidIncome(User voidedBy, String reason) {
		if (status != TransactionStatus.APPROVED) {
			throw new IllegalStateException("Only APPROVED income can be voided");
		}
		this.status = TransactionStatus.VOID;
		this.voidedByUser = voidedBy;
		this.voidedAt = Instant.now();
		this.voidReason = reason;
	}

	public void linkReceipt(Receipt receipt) {
		receipts.add(receipt);
		if (primaryReceipt == null) {
			primaryReceipt = receipt;
		}
	}
}
