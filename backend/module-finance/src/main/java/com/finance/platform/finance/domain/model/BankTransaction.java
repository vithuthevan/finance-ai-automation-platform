package com.finance.platform.finance.domain.model;

import com.finance.platform.core.domain.TenantAwareEntity;
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

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "bank_transactions")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BankTransaction extends TenantAwareEntity {

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "client_id", nullable = false)
	private Client client;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "bank_account_id")
	private BankAccount bankAccount;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "import_id", nullable = false)
	private BankImport bankImport;

	@Column(name = "txn_date", nullable = false)
	private LocalDate txnDate;

	@Column(name = "value_date")
	private LocalDate valueDate;

	@Column(length = 500)
	private String description;

	@Column(name = "reference_no", length = 100)
	private String referenceNo;

	@Column(precision = 19, scale = 4)
	private BigDecimal debit;

	@Column(precision = 19, scale = 4)
	private BigDecimal credit;

	@Column(precision = 19, scale = 4)
	private BigDecimal balance;

	@Enumerated(EnumType.STRING)
	@Column(length = 10)
	private TransactionDirection direction;

	@Column(nullable = false, length = 3)
	@Builder.Default
	private String currency = "LKR";

	@Column(name = "external_row_hash", length = 64)
	private String externalRowHash;

	@Enumerated(EnumType.STRING)
	@Column(name = "match_status", nullable = false, length = 20)
	@Builder.Default
	private MatchStatus matchStatus = MatchStatus.UNMATCHED;

	@Column(name = "ignore_reason", columnDefinition = "TEXT")
	private String ignoreReason;

	@Column(name = "pending_expense_id")
	private UUID pendingExpenseId;

	@Column(name = "pending_income_id")
	private UUID pendingIncomeId;

	public enum MatchStatus {
		UNMATCHED, SUGGESTED, MATCHED, BANK_ONLY, IGNORED, MISSING_RECEIPT, PENDING_APPROVAL
	}

	public enum TransactionDirection {
		DEBIT, CREDIT
	}

	public BigDecimal signedAmount() {
		if (credit != null && credit.signum() > 0) {
			return credit;
		}
		if (debit != null && debit.signum() > 0) {
			return debit;
		}
		return BigDecimal.ZERO;
	}

	public BigDecimal absoluteAmount() {
		return signedAmount().abs();
	}

	public boolean isDebit() {
		return direction == TransactionDirection.DEBIT
				|| (direction == null && debit != null && debit.signum() > 0);
	}
}
