package com.finance.platform.core.subscription;

import java.time.LocalDate;

public record SubscriptionUsageView(
		String planCode,
		String planName,
		String status,
		LocalDate periodFrom,
		LocalDate periodTo,
		InstantView trialEndsAt,
		UsageLimitView clients,
		UsageLimitView users,
		UsageLimitView documents,
		UsageLimitView aiProcessing,
		UsageLimitView storageBytes,
		boolean overLimit,
		boolean writeAllowed
) {
	public record InstantView(String value) {
	}
}
