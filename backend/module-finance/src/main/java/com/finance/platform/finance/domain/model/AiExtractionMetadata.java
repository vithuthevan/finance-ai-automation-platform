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

	@Column(name = "suggested_subtotal", precision = 19, scale = 4)
	private BigDecimal suggestedSubtotal;

	@JdbcTypeCode(SqlTypes.JSON)
	@Column(name = "line_items_json", columnDefinition = "jsonb")
	private String lineItemsJson;

	@Column(name = "supplier_confidence", precision = 5, scale = 4)
	private BigDecimal supplierConfidence;

	@Column(name = "date_confidence", precision = 5, scale = 4)
	private BigDecimal dateConfidence;

	@Column(name = "amount_confidence", precision = 5, scale = 4)
	private BigDecimal amountConfidence;

	@Column(name = "tax_confidence", precision = 5, scale = 4)
	private BigDecimal taxConfidence;

	@Column(name = "failure_code", length = 50)
	private String failureCode;

	@Column(name = "failure_message", length = 500)
	private String failureMessage;

	@Column(name = "review_outcome", length = 20)
	private String reviewOutcome;

	@Column(name = "amount_inconsistency")
	private Boolean amountInconsistency;

	@Column(name = "date_warning", length = 80)
	private String dateWarning;

	@Column(name = "ai_provider", length = 40)
	private String aiProvider;

	@Column(name = "input_tokens")
	private Integer inputTokens;

	@Column(name = "output_tokens")
	private Integer outputTokens;

	@Column(name = "processing_attempt_count")
	private Integer processingAttemptCount = 0;

	public enum ExtractionStatus {
		NOT_STARTED, PENDING, PROCESSING, COMPLETED, FAILED, AI_DISABLED
	}

	public enum SuggestedTransactionType {
		EXPENSE, INCOME, UNKNOWN
	}

	public enum ReviewOutcome {
		ACCEPTED, MODIFIED, REJECTED, MANUAL
	}
}
