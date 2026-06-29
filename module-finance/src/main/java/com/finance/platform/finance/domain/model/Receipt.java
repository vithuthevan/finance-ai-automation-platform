package com.finance.platform.finance.domain.model;

import com.finance.platform.core.domain.TenantAwareEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "receipts")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Receipt extends TenantAwareEntity {

	@Column(nullable = false)
	private UUID clientId;

	@Column(nullable = false)
	private UUID uploadedBy;

	@Column(nullable = false, length = 255)
	private String fileName;

	@Column(nullable = false, unique = true, length = 500)
	private String storageKey;

	@Column(nullable = false, length = 100)
	private String mimeType;

	@Column(nullable = false)
	private Long fileSizeBytes;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20)
	@Builder.Default
	private DocumentType documentType = DocumentType.RECEIPT;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20)
	@Builder.Default
	private ReceiptStatus status = ReceiptStatus.UPLOADED;

	@Embedded
	@Builder.Default
	private AiExtractionMetadata aiMetadata = new AiExtractionMetadata();

	private UUID suggestedCategoryId;
	private Instant uploadedAt;

	public enum DocumentType {
		RECEIPT, INVOICE, BANK_SLIP, OTHER
	}

	public enum ReceiptStatus {
		UPLOADED, PROCESSING, EXTRACTED, LINKED, FAILED
	}
}
