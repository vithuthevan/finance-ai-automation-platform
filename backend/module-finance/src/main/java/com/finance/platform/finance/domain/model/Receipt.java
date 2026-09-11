package com.finance.platform.finance.domain.model;

import com.finance.platform.auth.domain.model.User;
import com.finance.platform.core.domain.TenantAwareEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.HashSet;
import java.util.Set;

@Entity
@Table(name = "receipts", indexes = {
		@Index(columnList = "client_id"),
		@Index(columnList = "status")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Receipt extends TenantAwareEntity {

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "client_id", nullable = false)
	private Client client;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "uploaded_by", nullable = false)
	private User uploadedBy;

	@Column(nullable = false, length = 255)
	private String fileName;

	@Column(nullable = false, unique = true, length = 500)
	private String storageKey;

	@Column(nullable = false, length = 100)
	private String mimeType;

	@Column(nullable = false)
	private Long fileSizeBytes;

	@Column(length = 64)
	private String checksumSha256;

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

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "suggested_category_id")
	private Category suggestedCategory;

	@Column(columnDefinition = "TEXT")
	private String description;

	@Column(columnDefinition = "TEXT")
	private String reviewNote;

	private Instant reviewedAt;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "reviewed_by")
	private User reviewedBy;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "confirmed_category_id")
	private Category confirmedCategory;

	private Instant uploadedAt;
	private Instant deletedAt;

	@Version
	@Column(name = "row_version", nullable = false)
	@Builder.Default
	private Integer rowVersion = 0;

	@ManyToMany(mappedBy = "receipts")
	@Builder.Default
	private Set<Expense> expenses = new HashSet<>();

	@ManyToMany(mappedBy = "receipts")
	@Builder.Default
	private Set<Income> incomes = new HashSet<>();

	public enum DocumentType {
		RECEIPT, INVOICE, BANK_SLIP, OTHER, PURCHASE_INVOICE, SALES_INVOICE, BANK_STATEMENT, CREDIT_NOTE
	}

	public enum ReceiptStatus {
		UPLOADED, PROCESSING, EXTRACTED, NEEDS_REVIEW, LINKED, REJECTED, FAILED
	}
}
