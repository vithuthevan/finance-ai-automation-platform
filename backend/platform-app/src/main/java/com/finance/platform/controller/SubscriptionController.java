package com.finance.platform.controller;

import com.finance.platform.auth.domain.model.Role;
import com.finance.platform.auth.infrastructure.security.SecurityUtils;
import com.finance.platform.core.audit.AuditAction;
import com.finance.platform.core.audit.AuditEvent;
import com.finance.platform.core.audit.AuditLogger;
import com.finance.platform.core.audit.AuditResourceType;
import com.finance.platform.core.exception.BusinessException;
import com.finance.platform.core.exception.ErrorCodes;
import com.finance.platform.core.subscription.SubscriptionUsageView;
import com.finance.platform.finance.application.subscription.SubscriptionService;
import com.finance.platform.finance.application.subscription.UsageService;
import com.finance.platform.finance.domain.model.FirmSubscription;
import com.finance.platform.finance.domain.model.PlanChangeRequest;
import com.finance.platform.finance.infrastructure.persistence.FirmSubscriptionJpaRepository;
import com.finance.platform.finance.infrastructure.persistence.PlanChangeRequestJpaRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/subscription")
@RequiredArgsConstructor
@Tag(name = "Subscription", description = "Firm subscription and usage for administrators.")
public class SubscriptionController {

	private final UsageService usageService;
	private final SubscriptionService subscriptionService;
	private final PlanChangeRequestJpaRepository planChangeRequestRepository;
	private final FirmSubscriptionJpaRepository subscriptionRepository;
	private final AuditLogger auditLogger;

	@GetMapping
	@PreAuthorize("hasRole('ADMIN')")
	@Operation(summary = "Current subscription summary")
	public SubscriptionSummaryView summary() {
		UUID firmId = SecurityUtils.requireCurrentUser().getFirmId();
		FirmSubscription subscription = subscriptionService.requireForFirm(firmId);
		return SubscriptionSummaryView.from(subscription);
	}

	@GetMapping("/usage")
	@PreAuthorize("hasRole('ADMIN')")
	@Operation(summary = "Current plan usage against limits")
	public SubscriptionUsageView usage() {
		return usageService.getUsage(SecurityUtils.requireCurrentUser().getFirmId());
	}

	@PostMapping("/upgrade-request")
	@PreAuthorize("hasRole('ADMIN')")
	@Operation(summary = "Request a plan upgrade (manual approval)")
	public PlanChangeRequestView requestUpgrade(@RequestBody UpgradeRequest body) {
		var user = SecurityUtils.requireCurrentUser();
		if (user.getRole() != Role.RoleCode.ADMIN) {
			throw new BusinessException(ErrorCodes.ACCESS_DENIED, "Only firm administrators can request plan changes");
		}
		FirmSubscription subscription = subscriptionService.requireForFirm(user.getFirmId());
		PlanChangeRequest request = PlanChangeRequest.builder()
				.firmId(user.getFirmId())
				.requestedBy(user.getId())
				.currentPlanCode(subscription.getPlanCode())
				.requestedPlanCode(body.requestedPlanCode().trim().toUpperCase())
				.note(body.note())
				.build();
		PlanChangeRequest saved = planChangeRequestRepository.save(request);
		auditLogger.record(AuditEvent.fromTenant()
				.firmId(user.getFirmId())
				.action(AuditAction.PLAN_CHANGE_REQUESTED)
				.resourceType(AuditResourceType.SUBSCRIPTION)
				.resourceId(saved.getId())
				.afterState(Map.of(
						"currentPlan", saved.getCurrentPlanCode(),
						"requestedPlan", saved.getRequestedPlanCode()))
				.build());
		return PlanChangeRequestView.from(saved);
	}

	public record UpgradeRequest(String requestedPlanCode, String note) {
	}

	public record SubscriptionSummaryView(
			String planCode,
			String status,
			String periodFrom,
			String periodTo,
			String trialEndsAt
	) {
		static SubscriptionSummaryView from(FirmSubscription subscription) {
			return new SubscriptionSummaryView(
					subscription.getPlanCode(),
					subscription.getStatus().name(),
					subscription.getCurrentPeriodStart() == null ? null : subscription.getCurrentPeriodStart().toString(),
					subscription.getCurrentPeriodEnd() == null ? null : subscription.getCurrentPeriodEnd().toString(),
					subscription.getTrialEndsAt() == null ? null : subscription.getTrialEndsAt().toString()
			);
		}
	}

	public record PlanChangeRequestView(
			UUID id,
			String currentPlanCode,
			String requestedPlanCode,
			String status,
			Instant createdAt
	) {
		static PlanChangeRequestView from(PlanChangeRequest request) {
			return new PlanChangeRequestView(
					request.getId(),
					request.getCurrentPlanCode(),
					request.getRequestedPlanCode(),
					request.getStatus().name(),
					request.getCreatedAt()
			);
		}
	}
}
