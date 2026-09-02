package com.finance.platform.core.notification;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
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
@Table(name = "notification_deliveries")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class NotificationDelivery {

	@Id
	@GeneratedValue(strategy = GenerationType.UUID)
	private UUID id;

	@Column(nullable = false)
	private UUID notificationId;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20)
	private Channel channel;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20)
	private DeliveryStatus status;

	@Column(nullable = false)
	@Builder.Default
	private Instant attemptedAt = Instant.now();

	private Instant sentAt;

	@Column(columnDefinition = "TEXT")
	private String failureReason;

	public enum Channel {
		IN_APP, EMAIL
	}

	public enum DeliveryStatus {
		PENDING, SENT, FAILED
	}
}
