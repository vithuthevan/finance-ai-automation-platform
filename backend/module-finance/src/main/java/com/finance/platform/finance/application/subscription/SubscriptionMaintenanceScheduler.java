package com.finance.platform.finance.application.subscription;

import com.finance.platform.finance.domain.model.FirmSubscription;
import com.finance.platform.finance.domain.model.PlanChangeRequest;
import com.finance.platform.finance.infrastructure.persistence.FirmSubscriptionJpaRepository;
import com.finance.platform.finance.infrastructure.persistence.PlanChangeRequestJpaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;

@Component
@RequiredArgsConstructor
public class SubscriptionMaintenanceScheduler {

	private final FirmSubscriptionJpaRepository subscriptionRepository;
	private final SubscriptionService subscriptionService;
	private final UsageService usageService;
	private final SubscriptionNotificationService notificationService;

	@Scheduled(cron = "${app.subscription.maintenance-cron:0 30 7 * * *}")
	@Transactional
	public void maintainSubscriptions() {
		List<FirmSubscription> subscriptions = subscriptionRepository.findAll();
		for (FirmSubscription subscription : subscriptions) {
			handleTrial(subscription);
			handlePeriodRollover(subscription);
			notifyUsage(subscription);
		}
	}

	private void handleTrial(FirmSubscription subscription) {
		if (subscription.getStatus() != FirmSubscription.SubscriptionStatus.TRIAL || subscription.getTrialEndsAt() == null) {
			return;
		}
		long days = ChronoUnit.DAYS.between(LocalDate.now(),
				subscription.getTrialEndsAt().atZone(java.time.ZoneOffset.UTC).toLocalDate());
		if (days == 7 || days == 3 || days == 1) {
			notificationService.notifyTrialEnding(subscription.getFirmId(), (int) days);
		}
		if (usageService.isTrialExpired(subscription)) {
			subscription.setStatus(FirmSubscription.SubscriptionStatus.SUSPENDED);
			subscription.setSuspendedAt(Instant.now());
			subscriptionRepository.save(subscription);
			notificationService.notifyTrialEnding(subscription.getFirmId(), 0);
			notificationService.notifySubscriptionSuspended(subscription.getFirmId());
		}
	}

	private void handlePeriodRollover(FirmSubscription subscription) {
		if (subscription.getCurrentPeriodEnd() == null) {
			return;
		}
		if (subscription.getCurrentPeriodEnd().isBefore(LocalDate.now())) {
			subscriptionService.advanceBillingPeriod(subscription);
		}
	}

	private void notifyUsage(FirmSubscription subscription) {
		var usage = usageService.getUsage(subscription.getFirmId());
		notificationService.notifyUsageThreshold(subscription.getFirmId(), "DOCUMENTS", usage.documents());
		notificationService.notifyUsageThreshold(subscription.getFirmId(), "AI", usage.aiProcessing());
		notificationService.notifyUsageThreshold(subscription.getFirmId(), "STORAGE", usage.storageBytes());
	}
}
