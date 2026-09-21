package com.finance.platform.finance.domain.model.invoicing;

import com.finance.platform.core.domain.TenantAwareEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.UUID;

@Entity
@Table(name = "ar_customers")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ArCustomer extends TenantAwareEntity {

	@Column(name = "client_id")
	private UUID clientId;

	@Column(nullable = false, length = 200)
	private String name;

	@Column(length = 255)
	private String email;

	@Column(name = "payment_terms_days", nullable = false)
	@Builder.Default
	private int paymentTermsDays = 30;

	@Column(nullable = false)
	@Builder.Default
	private boolean active = true;
}
