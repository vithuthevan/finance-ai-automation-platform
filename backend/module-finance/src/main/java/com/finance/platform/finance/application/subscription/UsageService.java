package com.finance.platform.finance.application.subscription;

import com.finance.platform.core.exception.BusinessException;
import com.finance.platform.core.exception.ErrorCodes;
import com.finance.platform.core.exception.ResourceNotFoundException;
import com.finance.platform.core.subscription.SubscriptionUsageView;
import com.finance.platform.core.subscription.UsageLimitView;
import com.finance.platform.finance.domain.model.FirmSubscription;
import com.finance.platform.finance.domain.model.SubscriptionPlan;
import com.finance.platform.finance.infrastructure.persistence.FirmSubscriptionJpaRepository;
import com.finance.platform.finance.infrastructure.persistence.SubscriptionPlanJpaRepository;
import com.finance.platform.finance.infrastructure.persistence.UsageQueryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class UsageService {

	private final FirmSubscriptionJpaRepository subscriptionRepository;
	private final UsageQueryRepository usageQueries;

	@Transactional(readOnly = true)
	public SubscriptionUsageView getUsage(UUID firmId) {
		FirmSubscription subscription = requireSubscription(firmId);
		PeriodWindow period = resolvePeriod(subscription);
		long clients = usageQueries.countActiveClients(firmId);
		long users = usageQueries.countActiveUsers(firmId);
		long documents = usageQueries.countDocumentsInPeriod(firmId, period.fromInstant(), period.toInstant());
		long ai = usageQueries.countAiProcessingInPeriod(firmId, period.fromInstant(), period.toInstant());
		long storage = usageQueries.sumStorageBytes(firmId);

		UsageLimitView clientView = new UsageLimitView(clients, subscription.getMaxClients());
		UsageLimitView userView = new UsageLimitView(users, subscription.getMaxUsers());
		UsageLimitView docView = new UsageLimitView(documents, subscription.getMonthlyDocuments());
		UsageLimitView aiView = new UsageLimitView(ai, subscription.getAiMonthlyAllowance());
		UsageLimitView storageView = new UsageLimitView(storage, subscription.getStorageBytes());

		boolean overLimit = clientView.overLimit() || userView.overLimit() || docView.overLimit()
				|| aiView.overLimit() || storageView.overLimit();

		String planName = subscription.getPlan() != null ? subscription.getPlan().getName() : subscription.getPlanCode();
		return new SubscriptionUsageView(
				subscription.getPlanCode(),
				planName,
				subscription.getStatus().name(),
				period.from(),
				period.to(),
				subscription.getTrialEndsAt() == null ? null
						: new SubscriptionUsageView.InstantView(subscription.getTrialEndsAt().toString()),
				clientView,
				userView,
				docView,
				aiView,
				storageView,
				overLimit,
				subscription.allowsWrite() && !isTrialExpired(subscription)
		);
	}

	@Transactional(readOnly = true)
	public PeriodWindow resolvePeriod(FirmSubscription subscription) {
		LocalDate from = subscription.getCurrentPeriodStart();
		LocalDate to = subscription.getCurrentPeriodEnd();
		if (from == null || to == null) {
			LocalDate now = LocalDate.now();
			from = now.withDayOfMonth(1);
			to = from.plusMonths(1).minusDays(1);
		}
		return new PeriodWindow(from, to);
	}

	@Transactional(readOnly = true)
	public boolean isTrialExpired(FirmSubscription subscription) {
		return subscription.getStatus() == FirmSubscription.SubscriptionStatus.TRIAL
				&& subscription.getTrialEndsAt() != null
				&& subscription.getTrialEndsAt().isBefore(Instant.now());
	}

	private FirmSubscription requireSubscription(UUID firmId) {
		return subscriptionRepository.findByFirmId(firmId)
				.orElseThrow(() -> new ResourceNotFoundException("Subscription", firmId));
	}

	public record PeriodWindow(LocalDate from, LocalDate to) {
		public Instant fromInstant() {
			return from.atStartOfDay().toInstant(ZoneOffset.UTC);
		}

		public Instant toInstant() {
			return to.plusDays(1).atStartOfDay().toInstant(ZoneOffset.UTC);
		}
	}
}
