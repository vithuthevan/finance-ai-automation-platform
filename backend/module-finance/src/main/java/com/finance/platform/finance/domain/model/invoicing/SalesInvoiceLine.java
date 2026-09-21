package com.finance.platform.finance.domain.model.invoicing;

import com.finance.platform.core.domain.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.util.UUID;

@Entity
@Table(name = "sales_invoice_lines", uniqueConstraints = @UniqueConstraint(columnNames = {"invoice_id", "line_no"}))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SalesInvoiceLine extends BaseEntity {

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "invoice_id", nullable = false)
	private SalesInvoice invoice;

	@Column(name = "line_no", nullable = false)
	private int lineNo;

	@Column(nullable = false, length = 500)
	private String description;

	@Column(nullable = false, precision = 19, scale = 4)
	@Builder.Default
	private BigDecimal quantity = BigDecimal.ONE;

	@Column(name = "unit_price", nullable = false, precision = 19, scale = 4)
	@Builder.Default
	private BigDecimal unitPrice = BigDecimal.ZERO;

	@Column(name = "line_total", nullable = false, precision = 19, scale = 4)
	@Builder.Default
	private BigDecimal lineTotal = BigDecimal.ZERO;

	@Column(name = "tax_code", length = 40)
	private String taxCode;

	@Column(name = "tax_rate_percent", nullable = false, precision = 9, scale = 4)
	@Builder.Default
	private BigDecimal taxRatePercent = BigDecimal.ZERO;
}
