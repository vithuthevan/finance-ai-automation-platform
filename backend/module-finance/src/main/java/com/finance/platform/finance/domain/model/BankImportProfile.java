package com.finance.platform.finance.domain.model;

import com.finance.platform.core.domain.TenantAwareEntity;
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

@Entity
@Table(name = "bank_import_profiles")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BankImportProfile extends TenantAwareEntity {

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "client_id")
	private Client client;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "bank_account_id")
	private BankAccount bankAccount;

	@Column(name = "profile_name", nullable = false, length = 120)
	private String profileName;

	@Column(name = "date_column", nullable = false)
	@Builder.Default
	private int dateColumn = 0;

	@Column(name = "description_column", nullable = false)
	@Builder.Default
	private int descriptionColumn = 1;

	@Column(name = "reference_column", nullable = false)
	@Builder.Default
	private int referenceColumn = 2;

	@Column(name = "debit_column", nullable = false)
	@Builder.Default
	private int debitColumn = 3;

	@Column(name = "credit_column", nullable = false)
	@Builder.Default
	private int creditColumn = 4;

	@Column(name = "balance_column", nullable = false)
	@Builder.Default
	private int balanceColumn = 5;

	@Column(name = "amount_column")
	private Integer amountColumn;

	@Column(name = "date_format", nullable = false, length = 40)
	@Builder.Default
	private String dateFormat = "AUTO";

	@Column(name = "header_row", nullable = false)
	@Builder.Default
	private boolean headerRow = true;
}
