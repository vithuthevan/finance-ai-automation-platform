package com.finance.platform.finance.domain.model.recon;

import com.finance.platform.core.domain.BaseEntity;
import com.finance.platform.finance.domain.model.BankTransaction;
import com.finance.platform.finance.domain.model.Expense;
import com.finance.platform.finance.domain.model.Income;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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
import java.util.UUID;

@Entity
@Table(name = "reconciliation_match_group_items")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ReconciliationMatchGroupItem extends BaseEntity {

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "group_id", nullable = false)
	private ReconciliationMatchGroup group;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "bank_transaction_id")
	private BankTransaction bankTransaction;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "expense_id")
	private Expense expense;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "income_id")
	private Income income;

	@Column(name = "invoice_id")
	private UUID invoiceId;

	@Column(name = "payment_id")
	private UUID paymentId;

	@Column(name = "allocated_amount", precision = 19, scale = 4)
	private BigDecimal allocatedAmount;
}
