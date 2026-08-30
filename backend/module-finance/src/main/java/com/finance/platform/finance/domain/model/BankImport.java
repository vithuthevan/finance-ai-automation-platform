package com.finance.platform.finance.domain.model;

import com.finance.platform.auth.domain.model.User;
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
@Table(name = "bank_imports")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BankImport extends TenantAwareEntity {

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "client_id", nullable = false)
	private Client client;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "uploaded_by", nullable = false)
	private User uploadedBy;

	@Column(nullable = false, length = 255)
	private String fileName;

	@Column(nullable = false, length = 500)
	private String storageKey;

	@Column(nullable = false)
	@Builder.Default
	private int rowCount = 0;

	@Column(nullable = false, length = 20)
	@Builder.Default
	private String status = "IMPORTED";
}
