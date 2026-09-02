package com.finance.platform.finance.domain.model;

import com.finance.platform.auth.domain.model.User;
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

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

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

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "bank_account_id")
	private BankAccount bankAccount;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "uploaded_by", nullable = false)
	private User uploadedBy;

	@Column(nullable = false, length = 255)
	private String fileName;

	@Column(nullable = false, length = 500)
	private String storageKey;

	@Column(length = 64)
	private String checksum;

	private LocalDate periodFrom;
	private LocalDate periodTo;

	@Column(nullable = false)
	@Builder.Default
	private int rowCount = 0;

	@Column(nullable = false)
	@Builder.Default
	private int importedCount = 0;

	@Column(nullable = false)
	@Builder.Default
	private int duplicateCount = 0;

	@Column(nullable = false)
	@Builder.Default
	private int failedCount = 0;

	@Column(name = "import_status", nullable = false, length = 20)
	@Enumerated(EnumType.STRING)
	@Builder.Default
	private ImportStatus importStatus = ImportStatus.IMPORTED;

	/** Legacy column kept for compatibility with V13. */
	@Column(nullable = false, length = 20)
	@Builder.Default
	private String status = "IMPORTED";

	@Column(name = "error_message", columnDefinition = "TEXT")
	private String errorMessage;

	private Instant completedAt;

	@Column(name = "document_id")
	private UUID documentId;

	public enum ImportStatus {
		UPLOADED, MAPPED, VALIDATED, IMPORTED, FAILED
	}
}
