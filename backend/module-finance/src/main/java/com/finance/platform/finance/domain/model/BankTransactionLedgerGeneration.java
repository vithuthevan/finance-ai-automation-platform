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

@Entity
@Table(name = "bank_transaction_ledger_generations")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BankTransactionLedgerGeneration extends TenantAwareEntity {

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "client_id", nullable = false)
	private Client client;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "bank_transaction_id", nullable = false)
	private BankTransaction bankTransaction;

	@Enumerated(EnumType.STRING)
	@Column(name = "ledger_kind", nullable = false, length = 10)
	private LedgerKind ledgerKind;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "expense_id")
	private Expense expense;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "income_id")
	private Income income;

	public enum LedgerKind {
		EXPENSE, INCOME
	}
}
