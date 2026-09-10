package com.finance.platform.finance.application.subscription;



import com.finance.platform.core.exception.BusinessException;

import com.finance.platform.core.exception.ErrorCodes;

import com.finance.platform.core.subscription.SubscriptionQuotaGuard;

import com.finance.platform.finance.domain.model.FirmSubscription;

import com.finance.platform.finance.infrastructure.persistence.FirmSubscriptionJpaRepository;

import com.finance.platform.finance.infrastructure.persistence.UsageQueryRepository;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Service;

import org.springframework.transaction.annotation.Transactional;



import java.util.UUID;



@Service

@RequiredArgsConstructor

public class SubscriptionAccessService implements SubscriptionQuotaGuard {



	private final FirmSubscriptionJpaRepository subscriptionRepository;

	private final UsageQueryRepository usageQueries;

	private final UsageService usageService;



	@Override

	@Transactional(readOnly = true)

	public void assertCanWrite(UUID firmId) {

		FirmSubscription subscription = requireSubscription(firmId);

		if (usageService.isTrialExpired(subscription)) {

			throw new BusinessException(ErrorCodes.SUBSCRIPTION_INACTIVE,

					"Your trial has ended. Existing data remains available in read-only mode.");

		}

		if (!subscription.allowsWrite()) {

			throw blockedStatusException(subscription.getStatus());

		}

	}



	@Override

	@Transactional

	public void assertCanCreateActiveClient(UUID firmId) {

		assertCanWrite(firmId);

		FirmSubscription subscription = lockSubscription(firmId);

		long active = usageQueries.countActiveClients(firmId);

		if (active >= subscription.getMaxClients()) {

			throw new BusinessException(ErrorCodes.PLAN_CLIENT_LIMIT_REACHED,

					"Your current plan supports up to " + subscription.getMaxClients() + " active clients.");

		}

	}



	@Override

	@Transactional

	public void assertCanCreateActiveUser(UUID firmId) {

		assertCanWrite(firmId);

		FirmSubscription subscription = lockSubscription(firmId);

		long active = usageQueries.countActiveUsers(firmId);

		if (active >= subscription.getMaxUsers()) {

			throw new BusinessException(ErrorCodes.PLAN_USER_LIMIT_REACHED,

					"Your current plan supports up to " + subscription.getMaxUsers() + " active users.");

		}

	}



	@Override

	@Transactional

	public void assertCanUploadDocument(UUID firmId, long incomingFileBytes) {

		assertCanWrite(firmId);

		FirmSubscription subscription = lockSubscription(firmId);

		UsageService.PeriodWindow period = usageService.resolvePeriod(subscription);

		long documents = usageQueries.countDocumentsInPeriod(firmId, period.fromInstant(), period.toInstant());

		if (documents >= subscription.getMonthlyDocuments()) {

			throw new BusinessException(ErrorCodes.PLAN_DOCUMENT_LIMIT_REACHED,

					"Monthly document upload limit reached for your plan.");

		}

		long storage = usageQueries.sumStorageBytes(firmId);

		if (storage + incomingFileBytes > subscription.getStorageBytes()) {

			throw new BusinessException(ErrorCodes.PLAN_STORAGE_LIMIT_REACHED,

					"Storage limit reached for your plan.");

		}

	}



	@Override

	@Transactional(readOnly = true)

	public boolean canProcessAi(UUID firmId) {

		try {

			assertCanWrite(firmId);

		} catch (BusinessException ex) {

			return false;

		}

		FirmSubscription subscription = requireSubscription(firmId);

		UsageService.PeriodWindow period = usageService.resolvePeriod(subscription);

		long ai = usageQueries.countAiProcessingInPeriod(firmId, period.fromInstant(), period.toInstant());

		return ai < subscription.getAiMonthlyAllowance();

	}



	@Override

	@Transactional(readOnly = true)

	public String aiQuotaMessage(UUID firmId) {

		return "AI processing limit reached for this billing period. The document is still available for manual review.";

	}



	private FirmSubscription requireSubscription(UUID firmId) {

		return subscriptionRepository.findByFirmId(firmId)

				.orElseThrow(() -> new BusinessException(ErrorCodes.SUBSCRIPTION_NOT_FOUND, "Subscription not found"));

	}



	private FirmSubscription lockSubscription(UUID firmId) {

		return subscriptionRepository.findByFirmIdForUpdate(firmId)

				.orElseThrow(() -> new BusinessException(ErrorCodes.SUBSCRIPTION_NOT_FOUND, "Subscription not found"));

	}



	private static BusinessException blockedStatusException(FirmSubscription.SubscriptionStatus status) {

		return switch (status) {

			case SUSPENDED -> new BusinessException(ErrorCodes.SUBSCRIPTION_SUSPENDED,

					"Your subscription is suspended. Existing data remains available in read-only mode.");

			case CANCELLED -> new BusinessException(ErrorCodes.SUBSCRIPTION_INACTIVE,

					"Your subscription is cancelled. Existing data remains available in read-only mode.");

			default -> new BusinessException(ErrorCodes.SUBSCRIPTION_INACTIVE,

					"Subscription is not active for write operations.");

		};

	}

}


