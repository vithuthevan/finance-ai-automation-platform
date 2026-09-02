package com.finance.platform.finance.application.subscription;

import com.finance.platform.core.audit.AuditAction;
import com.finance.platform.core.audit.AuditEvent;
import com.finance.platform.core.audit.AuditLogger;
import com.finance.platform.core.audit.AuditResourceType;
import com.finance.platform.core.exception.BusinessException;
import com.finance.platform.core.exception.ErrorCodes;
import com.finance.platform.core.exception.ResourceNotFoundException;
import com.finance.platform.finance.domain.model.FirmSubscription;
import com.finance.platform.finance.domain.model.SubscriptionPlan;
import com.finance.platform.finance.infrastructure.persistence.FirmSubscriptionJpaRepository;
import com.finance.platform.finance.infrastructure.persistence.SubscriptionPlanJpaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class SubscriptionService {

	private final FirmSubscriptionJpaRepository subscriptionRepository;
	private final SubscriptionPlanJpaRepository planRepository;
	private final SubscriptionProperties properties;
	private final BillingProvider billingProvider;
	private final AuditLogger auditLogger;

	@Transactional(readOnly = true)
	public FirmSubscription requireForFirm(UUID firmId) {
		return subscriptionRepository.findByFirmId(firmId)
				.orElseThrow(() -> new ResourceNotFoundException("Subscription", firmId));
	}

	@Transactional
	public FirmSubscription createDefaultForFirm(UUID firmId) {
		SubscriptionPlan plan = planRepository.findByCodeAndActiveTrue(properties.getDefaultPlan())
				.orElseGet(() -> planRepository.findByCode("STARTER")
						.orElseThrow(() -> new BusinessException(ErrorCodes.PLAN_NOT_FOUND, "Default plan not configured")));
		LocalDate periodStart = LocalDate.now().withDayOfMonth(1);
		LocalDate periodEnd = periodStart.plusMonths(1).minusDays(1);
		Instant trialEnds = Instant.now().plusSeconds(properties.getDefaultTrialDays() * 24L * 3600L);

		FirmSubscription subscription = FirmSubscription.builder()
				.firmId(firmId)
				.plan(plan)
				.planCode(plan.getCode())
				.status(FirmSubscription.SubscriptionStatus.TRIAL)
				.maxClients(plan.getMaxClients())
				.maxUsers(plan.getMaxUsers())
				.monthlyDocuments(plan.getMonthlyDocumentLimit())
				.aiMonthlyAllowance(plan.getMonthlyAiLimit())
				.storageBytes(plan.getStorageLimitBytes())
				.startedAt(Instant.now())
				.currentPeriodStart(periodStart)
				.currentPeriodEnd(periodEnd)
				.trialEndsAt(trialEnds)
				.build();
		FirmSubscription saved = subscriptionRepository.save(subscription);
		billingProvider.onSubscriptionCreated(new BillingProvider.FirmSubscriptionContext(
				firmId, saved.getPlanCode(), saved.getStatus().name()));
		auditLogger.record(AuditEvent.builder()
				.firmId(firmId)
				.action(AuditAction.SUBSCRIPTION_CREATED)
				.resourceType(AuditResourceType.SUBSCRIPTION)
				.resourceId(saved.getId())
				.afterState(subscriptionSnapshot(saved))
				.build());
		return saved;
	}

	@Transactional
	public FirmSubscription applyPlan(UUID firmId, String planCode, UUID actorUserId, String reason) {
		FirmSubscription subscription = requireForFirm(firmId);
		SubscriptionPlan plan = planRepository.findByCodeAndActiveTrue(planCode)
				.orElseThrow(() -> new BusinessException(ErrorCodes.PLAN_NOT_FOUND, "Plan not found: " + planCode));
		String previous = subscription.getPlanCode();
		subscription.setPlan(plan);
		subscription.setPlanCode(plan.getCode());
		subscription.setMaxClients(plan.getMaxClients());
		subscription.setMaxUsers(plan.getMaxUsers());
		subscription.setMonthlyDocuments(plan.getMonthlyDocumentLimit());
		subscription.setAiMonthlyAllowance(plan.getMonthlyAiLimit());
		subscription.setStorageBytes(plan.getStorageLimitBytes());
		FirmSubscription saved = subscriptionRepository.save(subscription);
		billingProvider.onPlanChanged(
				new BillingProvider.FirmSubscriptionContext(firmId, saved.getPlanCode(), saved.getStatus().name()),
				previous);
		Map<String, Object> after = subscriptionSnapshot(saved);
		after.put("reason", reason == null ? "" : reason);
		after.put("actorUserId", actorUserId == null ? "" : actorUserId.toString());
		auditLogger.record(AuditEvent.builder()
				.firmId(firmId)
				.actorUserId(actorUserId)
				.action(AuditAction.SUBSCRIPTION_PLAN_CHANGED)
				.resourceType(AuditResourceType.SUBSCRIPTION)
				.resourceId(saved.getId())
				.beforeState(Map.of("planCode", previous))
				.afterState(after)
				.build());
		return saved;
	}

	@Transactional
	public FirmSubscription updateStatus(UUID firmId, FirmSubscription.SubscriptionStatus status, UUID actorUserId, String reason) {
		FirmSubscription subscription = requireForFirm(firmId);
		String previous = subscription.getStatus().name();
		subscription.setStatus(status);
		if (status == FirmSubscription.SubscriptionStatus.SUSPENDED) {
			subscription.setSuspendedAt(Instant.now());
		} else if (status == FirmSubscription.SubscriptionStatus.CANCELLED) {
			subscription.setCancelledAt(Instant.now());
		} else if (status == FirmSubscription.SubscriptionStatus.ACTIVE) {
			subscription.setSuspendedAt(null);
		}
		FirmSubscription saved = subscriptionRepository.save(subscription);
		AuditAction action = switch (status) {
			case ACTIVE, TRIAL -> AuditAction.SUBSCRIPTION_ACTIVATED;
			case SUSPENDED -> AuditAction.SUBSCRIPTION_SUSPENDED;
			default -> AuditAction.SUBSCRIPTION_PLAN_CHANGED;
		};
		Map<String, Object> after = subscriptionSnapshot(saved);
		after.put("reason", reason == null ? "" : reason);
		auditLogger.record(AuditEvent.builder()
				.firmId(firmId)
				.actorUserId(actorUserId)
				.action(action)
				.resourceType(AuditResourceType.SUBSCRIPTION)
				.resourceId(saved.getId())
				.beforeState(Map.of("status", previous))
				.afterState(after)
				.build());
		return saved;
	}

	@Transactional
	public FirmSubscription extendTrial(UUID firmId, int additionalDays, UUID actorUserId, String reason) {
		FirmSubscription subscription = requireForFirm(firmId);
		Instant base = subscription.getTrialEndsAt() == null ? Instant.now() : subscription.getTrialEndsAt();
		subscription.setTrialEndsAt(base.plusSeconds(additionalDays * 24L * 3600L));
		if (subscription.getStatus() == FirmSubscription.SubscriptionStatus.SUSPENDED) {
			subscription.setStatus(FirmSubscription.SubscriptionStatus.TRIAL);
			subscription.setSuspendedAt(null);
		}
		FirmSubscription saved = subscriptionRepository.save(subscription);
		auditLogger.record(AuditEvent.builder()
				.firmId(firmId)
				.actorUserId(actorUserId)
				.action(AuditAction.TRIAL_EXTENDED)
				.resourceType(AuditResourceType.SUBSCRIPTION)
				.resourceId(saved.getId())
				.afterState(Map.of(
						"trialEndsAt", saved.getTrialEndsAt().toString(),
						"reason", reason == null ? "" : reason))
				.build());
		return saved;
	}

	@Transactional
	public void advanceBillingPeriod(FirmSubscription subscription) {
		LocalDate nextStart = subscription.getCurrentPeriodEnd() == null
				? LocalDate.now().withDayOfMonth(1)
				: subscription.getCurrentPeriodEnd().plusDays(1);
		subscription.setCurrentPeriodStart(nextStart);
		subscription.setCurrentPeriodEnd(nextStart.plusMonths(1).minusDays(1));
		subscriptionRepository.save(subscription);
	}

	private static Map<String, Object> subscriptionSnapshot(FirmSubscription subscription) {
		Map<String, Object> state = new LinkedHashMap<>();
		state.put("planCode", subscription.getPlanCode());
		state.put("status", subscription.getStatus().name());
		state.put("maxClients", subscription.getMaxClients());
		state.put("maxUsers", subscription.getMaxUsers());
		state.put("monthlyDocuments", subscription.getMonthlyDocuments());
		state.put("aiMonthlyAllowance", subscription.getAiMonthlyAllowance());
		state.put("storageBytes", subscription.getStorageBytes());
		return state;
	}
}
