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

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "import_id", nullable = false)
	private BankImport bankImport;

	@Column(name = "txn_date", nullable = false)
	private LocalDate txnDate;

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
	@Column(nullable = false, length = 20)
	@Builder.Default
	private MatchStatus matchStatus = MatchStatus.UNMATCHED;

	public enum MatchStatus {
		UNMATCHED, SUGGESTED, MATCHED, BANK_ONLY, IGNORED, MISSING_RECEIPT
	}

	public BigDecimal signedAmount() {
		if (credit != null && credit.signum() > 0) {
			return credit;
		}
		if (debit != null) {
			return debit.negate();
		}
		return BigDecimal.ZERO;
	}
}
