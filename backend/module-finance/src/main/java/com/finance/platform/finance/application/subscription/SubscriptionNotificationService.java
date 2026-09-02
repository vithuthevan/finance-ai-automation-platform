package com.finance.platform.finance.application.subscription;

import com.finance.platform.auth.domain.model.Role;
import com.finance.platform.auth.infrastructure.persistence.UserJpaRepository;
import com.finance.platform.core.notification.NotificationCommand;
import com.finance.platform.core.notification.NotificationDispatcher;
import com.finance.platform.core.notification.NotificationType;
import com.finance.platform.core.subscription.UsageLimitView;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class SubscriptionNotificationService {

	private final NotificationDispatcher notificationDispatcher;
	private final UserJpaRepository userRepository;

	@Transactional(readOnly = true)
	public void notifyUsageThreshold(UUID firmId, String metric, UsageLimitView usage) {
		if (!usage.atOrAboveThreshold(80)) {
			return;
		}
		Set<UUID> admins = adminIds(firmId);
		if (admins.isEmpty()) {
			return;
		}
		notificationDispatcher.dispatch(new NotificationCommand(
				firmId,
				admins,
				null,
				NotificationType.SUBSCRIPTION_USAGE_WARNING,
				metric + " usage at " + usage.percentUsed() + "%",
				"Your plan " + metric.toLowerCase() + " usage is " + usage.used() + " of " + usage.limit() + ".",
				"SUBSCRIPTION",
				null,
				"/app/subscription",
				"usage-threshold:" + firmId + ":" + metric + ":" + usage.percentUsed(),
				false
		));
	}

	@Transactional(readOnly = true)
	public void notifyTrialEnding(UUID firmId, int daysRemaining) {
		Set<UUID> admins = adminIds(firmId);
		if (admins.isEmpty()) {
			return;
		}
		notificationDispatcher.dispatch(new NotificationCommand(
				firmId,
				admins,
				null,
				NotificationType.TRIAL_ENDING,
				"Trial ending soon",
				daysRemaining <= 0 ? "Your trial has ended." : daysRemaining + " day(s) remain on your trial.",
				"SUBSCRIPTION",
				null,
				"/app/subscription",
				"trial-ending:" + firmId + ":" + daysRemaining,
				true
		));
	}

	@Transactional(readOnly = true)
	public void notifySubscriptionSuspended(UUID firmId) {
		Set<UUID> admins = adminIds(firmId);
		if (admins.isEmpty()) {
			return;
		}
		notificationDispatcher.dispatch(new NotificationCommand(
				firmId,
				admins,
				null,
				NotificationType.SUBSCRIPTION_SUSPENDED,
				"Subscription suspended",
				"Your subscription is suspended. Existing data remains available in read-only mode.",
				"SUBSCRIPTION",
				null,
				"/app/subscription",
				"subscription-suspended:" + firmId,
				true
		));
	}

	private Set<UUID> adminIds(UUID firmId) {
		return userRepository.findByFirmIdAndDeletedAtIsNull(firmId).stream()
				.filter(user -> user.getRole().getCode() == Role.RoleCode.ADMIN)
				.map(user -> user.getId())
				.collect(Collectors.toSet());
	}
}
