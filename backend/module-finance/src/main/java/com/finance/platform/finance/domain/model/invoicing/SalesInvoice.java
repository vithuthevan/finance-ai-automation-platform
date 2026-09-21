package com.finance.platform.finance.domain.model.invoicing;

import com.finance.platform.core.domain.TenantAwareEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "sales_invoices")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SalesInvoice extends TenantAwareEntity {

	public enum DocumentStatus {
		DRAFT, ISSUED, VOID
	}

	public enum SettlementStatus {
		UNPAID, PARTIALLY_PAID, PAID, OVERDUE
	}

	@Column(name = "customer_id", nullable = false)
	private UUID customerId;

	@Column(name = "invoice_number", nullable = false, length = 40)
	private String invoiceNumber;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20)
	@Builder.Default
	private DocumentStatus status = DocumentStatus.DRAFT;

	@Enumerated(EnumType.STRING)
	@Column(name = "settlement_status", nullable = false, length = 20)
	@Builder.Default
	private SettlementStatus settlementStatus = SettlementStatus.UNPAID;

	@Column(name = "issue_date")
	private LocalDate issueDate;

	@Column(name = "due_date")
	private LocalDate dueDate;

	@Column(nullable = false, length = 3)
	@Builder.Default
	private String currency = "LKR";

	@Column(nullable = false, precision = 19, scale = 4)
	@Builder.Default
	private BigDecimal subtotal = BigDecimal.ZERO;

	@Column(name = "tax_total", nullable = false, precision = 19, scale = 4)
	@Builder.Default
	private BigDecimal taxTotal = BigDecimal.ZERO;

	@Column(nullable = false, precision = 19, scale = 4)
	@Builder.Default
	private BigDecimal total = BigDecimal.ZERO;

	@Column(columnDefinition = "TEXT")
	private String notes;

	@Version
	@Column(name = "row_version", nullable = false)
	@Builder.Default
	private int rowVersion = 0;
}
