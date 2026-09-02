package com.finance.platform.core.notification;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "notification_preferences")
@Getter
@Setter
@NoArgsConstructor
public class NotificationPreference {

	@Id
	@GeneratedValue(strategy = GenerationType.UUID)
	private UUID id;

	@Column(nullable = false)
	private UUID firmId;

	@Column(nullable = false, unique = true)
	private UUID userId;

	@Column(nullable = false)
	private boolean emailEnabled = true;

	@Column(nullable = false)
	private boolean emailDocumentRequested = true;

	@Column(nullable = false)
	private boolean emailDocumentUploaded = true;

	@Column(nullable = false)
	private boolean emailPeriodReady = true;

	@Column(nullable = false)
	private Instant createdAt = Instant.now();

	@Column(nullable = false)
	private Instant updatedAt = Instant.now();

	public boolean allowsEmail(String type) {
		if (!emailEnabled) {
			return false;
		}
		if (NotificationType.DOCUMENT_REQUEST_CREATED.equals(type)) {
			return emailDocumentRequested;
		}
		if (NotificationType.DOCUMENT_REQUEST_UPLOADED.equals(type)) {
			return emailDocumentUploaded;
		}
		if (NotificationType.PERIOD_READY_TO_CLOSE.equals(type)) {
			return emailPeriodReady;
		}
		return true;
	}
}
