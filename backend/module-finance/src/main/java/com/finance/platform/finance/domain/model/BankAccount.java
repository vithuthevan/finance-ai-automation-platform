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
@Table(name = "bank_accounts")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BankAccount extends TenantAwareEntity {

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "client_id", nullable = false)
	private Client client;

	@Column(name = "bank_name", nullable = false, length = 120)
	private String bankName;

	@Column(name = "account_name", nullable = false, length = 120)
	private String accountName;

	@Column(name = "masked_account_number", length = 32)
	private String maskedAccountNumber;

	@Column(nullable = false, length = 3)
	@Builder.Default
	private String currency = "LKR";

	@Column(nullable = false)
	@Builder.Default
	private boolean active = true;

	public String displayAccountNumber() {
		return maskedAccountNumber == null || maskedAccountNumber.isBlank() ? "—" : maskedAccountNumber;
	}
}
