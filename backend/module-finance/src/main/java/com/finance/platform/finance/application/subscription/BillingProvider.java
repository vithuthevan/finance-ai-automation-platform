package com.finance.platform.finance.application.subscription;

public interface BillingProvider {

	String providerName();

	default void onSubscriptionCreated(FirmSubscriptionContext context) {
	}

	default void onPlanChanged(FirmSubscriptionContext context, String previousPlanCode) {
	}

	record FirmSubscriptionContext(
			java.util.UUID firmId,
			String planCode,
			String status
	) {
	}
}
