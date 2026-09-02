package com.finance.platform.core.notification;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class NotificationDispatcher {

	private final NotificationJpaRepository notificationRepository;
	private final NotificationDeliveryJpaRepository deliveryRepository;
	private final NotificationPreferenceService preferenceService;
	private final EmailService emailService;
	private final UserEmailLookup userEmailLookup;

	@Transactional
	public void dispatch(NotificationCommand command) {
		if (command.recipientUserIds() == null || command.recipientUserIds().isEmpty()) {
			return;
		}
		Set<UUID> recipients = new HashSet<>(command.recipientUserIds());
		for (UUID userId : recipients) {
			if (command.dedupeKey() != null
					&& notificationRepository.existsByUserIdAndDedupeKeyAndReadAtIsNull(userId, command.dedupeKey())) {
				continue;
			}
			Notification saved = notificationRepository.save(Notification.builder()
					.firmId(command.firmId())
					.userId(userId)
					.clientId(command.clientId())
					.type(command.type())
					.title(command.title())
					.message(command.message())
					.resourceType(command.resourceType())
					.resourceId(command.resourceId())
					.actionUrl(command.actionUrl())
					.dedupeKey(command.dedupeKey())
					.build());
			if (command.sendEmail() && preferenceService.emailEnabledFor(userId, command.type())) {
				sendEmail(userId, saved);
			}
		}
	}

	private void sendEmail(UUID userId, Notification notification) {
		String email = userEmailLookup.findEmail(userId).orElse(null);
		if (email == null || email.isBlank()) {
			recordDelivery(notification.getId(), NotificationDelivery.Channel.EMAIL,
					NotificationDelivery.DeliveryStatus.FAILED, "No email address");
			return;
		}
		String sanitizedTo = email.replaceAll("[\\r\\n]", "").trim();
		String sanitizedSubject = notification.getTitle().replaceAll("[\\r\\n]", " ").trim();
		NotificationDelivery delivery = deliveryRepository.save(NotificationDelivery.builder()
				.notificationId(notification.getId())
				.channel(NotificationDelivery.Channel.EMAIL)
				.status(NotificationDelivery.DeliveryStatus.PENDING)
				.build());
		try {
			emailService.send(sanitizedTo, sanitizedSubject, notification.getMessage());
			delivery.setStatus(NotificationDelivery.DeliveryStatus.SENT);
			delivery.setSentAt(Instant.now());
		} catch (Exception ex) {
			log.warn("Email delivery failed for notification {}: {}", notification.getId(), ex.getMessage());
			delivery.setStatus(NotificationDelivery.DeliveryStatus.FAILED);
			delivery.setFailureReason(ex.getMessage());
		}
		deliveryRepository.save(delivery);
	}

	private void recordDelivery(UUID notificationId, NotificationDelivery.Channel channel,
			NotificationDelivery.DeliveryStatus status, String reason) {
		deliveryRepository.save(NotificationDelivery.builder()
				.notificationId(notificationId)
				.channel(channel)
				.status(status)
				.failureReason(reason)
				.build());
	}

	/**
	 * Indirection so platform-core does not depend on module-auth.
	 */
	public interface UserEmailLookup {
		java.util.Optional<String> findEmail(UUID userId);
	}
}
