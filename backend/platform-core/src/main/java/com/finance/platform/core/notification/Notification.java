package com.finance.platform.core.notification;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "notifications")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Notification {

	@Id
	@GeneratedValue(strategy = GenerationType.UUID)
	private UUID id;

	@Column(nullable = false, updatable = false)
	private UUID firmId;

	@Column(nullable = false)
	private UUID userId;

	private UUID clientId;

	@Column(nullable = false, length = 80)
	private String type;

	@Column(nullable = false, length = 200)
	private String title;

	@Column(nullable = false, columnDefinition = "TEXT")
	private String message;

	@Column(length = 40)
	private String resourceType;

	private UUID resourceId;

	@Column(length = 500)
	private String actionUrl;

	@Column(length = 120)
	private String dedupeKey;

	private Instant readAt;

	@Column(nullable = false)
	@Builder.Default
	private Instant createdAt = Instant.now();

	public boolean isRead() {
		return readAt != null;
	}
}
