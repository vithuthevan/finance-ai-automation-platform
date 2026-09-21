package com.finance.platform.finance.application.dto.chase;

import java.time.Instant;
import java.util.UUID;

public record ClientChaseRunResponse(
		UUID id,
		UUID clientId,
		String clientName,
		String sourceType,
		UUID sourceId,
		String status,
		Instant startedAt,
		int reminderCount,
		Instant lastReminderAt,
		String lastDeliveryStatus
) {
}
