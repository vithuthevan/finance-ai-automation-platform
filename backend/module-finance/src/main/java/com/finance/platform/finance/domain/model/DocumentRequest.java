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
@Table(name = "document_requests")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DocumentRequest extends TenantAwareEntity {

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "client_id", nullable = false)
	private Client client;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "period_id")
	private AccountingPeriod period;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "requested_by", nullable = false)
	private User requestedBy;

	@Column(name = "assignee_user_id")
	private UUID assigneeUserId;

	@Column(nullable = false, columnDefinition = "TEXT")
	private String description;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20)
	@Builder.Default
	private Receipt.DocumentType documentType = Receipt.DocumentType.OTHER;

	private LocalDate dueDate;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20)
	@Builder.Default
	private RequestStatus status = RequestStatus.OPEN;

	@Column(name = "uploaded_document_id")
	private UUID uploadedDocumentId;

	private Instant completedAt;

	public enum RequestStatus {
		OPEN, UPLOADED, COMPLETED, CANCELLED
	}
}
