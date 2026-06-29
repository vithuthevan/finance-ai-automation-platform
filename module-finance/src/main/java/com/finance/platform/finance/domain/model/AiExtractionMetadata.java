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
	@Column(length = 20)
	private ExtractionStatus extractionStatus;

	@Column(precision = 5, scale = 4)
	private BigDecimal confidenceScore;

	@Column(length = 50)
	private String modelVersion;

	@Enumerated(EnumType.STRING)
	@Column(length = 10)
	private SuggestedTransactionType suggestedType;

	@Column(length = 200)
	private String suggestedVendorOrCustomer;

	private LocalDate suggestedDate;

	@Column(precision = 19, scale = 4)
	private BigDecimal suggestedAmount;

	@Column(columnDefinition = "TEXT")
	private String ocrText;

	@Column(columnDefinition = "jsonb")
	private String rawExtractionJson;

	private Instant processedAt;

	public enum ExtractionStatus {
		NOT_STARTED, PENDING, PROCESSING, COMPLETED, FAILED
	}

	public enum SuggestedTransactionType {
		EXPENSE, INCOME
	}
}
