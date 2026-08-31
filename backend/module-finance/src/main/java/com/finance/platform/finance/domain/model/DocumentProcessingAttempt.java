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
@Table(name = "document_processing_attempts")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DocumentProcessingAttempt extends TenantAwareEntity {

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "receipt_id", nullable = false)
	private Receipt receipt;

	@Column(name = "attempt_no", nullable = false)
	private int attemptNo;

	@Column(length = 40)
	private String provider;

	@Column(name = "model_version", length = 80)
	private String modelVersion;

	@Column(nullable = false, length = 20)
	private String status;

	@Column(name = "failure_code", length = 50)
	private String failureCode;

	@Column(name = "failure_message", length = 500)
	private String failureMessage;

	@Column(name = "duration_ms")
	private Integer durationMs;

	@Column(name = "input_tokens")
	private Integer inputTokens;

	@Column(name = "output_tokens")
	private Integer outputTokens;
}
