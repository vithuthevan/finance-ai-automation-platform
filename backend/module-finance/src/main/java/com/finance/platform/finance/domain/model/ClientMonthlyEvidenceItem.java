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
@Table(name = "client_monthly_evidence_items")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ClientMonthlyEvidenceItem extends TenantAwareEntity {

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "client_id", nullable = false)
	private Client client;

	@Column(nullable = false, length = 200)
	private String title;

	@Column(columnDefinition = "TEXT")
	private String description;

	@Enumerated(EnumType.STRING)
	@Column(name = "document_type", nullable = false, length = 20)
	@Builder.Default
	private Receipt.DocumentType documentType = Receipt.DocumentType.OTHER;

	@Column(nullable = false)
	@Builder.Default
	private boolean required = true;

	@Enumerated(EnumType.STRING)
	@Column(name = "responsible_party", nullable = false, length = 20)
	@Builder.Default
	private ResponsibleParty responsibleParty = ResponsibleParty.CLIENT;

	@Column(nullable = false)
	@Builder.Default
	private boolean active = true;

	@Column(name = "sort_order", nullable = false)
	@Builder.Default
	private int sortOrder = 0;

	public enum ResponsibleParty {
		CLIENT,
		FIRM
	}
}
