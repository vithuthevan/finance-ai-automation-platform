package com.finance.platform.core.notification;

import java.util.Set;
import java.util.UUID;

public record NotificationCommand(
		UUID firmId,
		Set<UUID> recipientUserIds,
		UUID clientId,
		String type,
		String title,
		String message,
		String resourceType,
		UUID resourceId,
		String actionUrl,
		String dedupeKey,
		boolean sendEmail
) {
}
