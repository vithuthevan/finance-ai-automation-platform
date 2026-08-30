package com.finance.platform.finance.domain.model;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

@Embeddable
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AiExtractionMetadata {

	@Enumerated(EnumType.STRING)
	@Column(name = "extraction_status", length = 20)
	private ExtractionStatus extractionStatus;

	@Column(name = "confidence_score", precision = 5, scale = 4)
	private BigDecimal confidenceScore;

	@Column(name = "model_version", length = 50)
	private String modelVersion;

	@Column(name = "prompt_template_id", length = 50)
	private String promptTemplateId;

	@Enumerated(EnumType.STRING)
	@Column(name = "suggested_type", length = 10)
	private SuggestedTransactionType suggestedType;

	@Column(name = "suggested_vendor_or_customer", length = 200)
	private String suggestedVendorOrCustomer;

	@Column(name = "suggested_date")
	private LocalDate suggestedDate;

	@Column(name = "suggested_amount", precision = 19, scale = 4)
	private BigDecimal suggestedAmount;

	@Column(name = "suggested_invoice_no", length = 100)
	private String suggestedInvoiceNo;

	@Column(name = "suggested_due_date")
	private LocalDate suggestedDueDate;

	@Column(name = "suggested_currency", length = 3)
	private String suggestedCurrency;

	@Column(name = "suggested_tax_amount", precision = 19, scale = 4)
	private BigDecimal suggestedTaxAmount;

	@Column(name = "suggested_payment_method", length = 20)
	private String suggestedPaymentMethod;

	@Column(name = "suggested_description", columnDefinition = "TEXT")
	private String suggestedDescription;

	@Column(name = "ocr_text", columnDefinition = "TEXT")
	private String ocrText;

	@JdbcTypeCode(SqlTypes.JSON)
	@Column(name = "raw_extraction_json", columnDefinition = "jsonb")
	private String rawExtractionJson;

	@Column(name = "ai_processed_at")
	private Instant processedAt;

	public enum ExtractionStatus {
		NOT_STARTED, PENDING, PROCESSING, COMPLETED, FAILED, AI_DISABLED
	}

	public enum SuggestedTransactionType {
		EXPENSE, INCOME
	}
}
