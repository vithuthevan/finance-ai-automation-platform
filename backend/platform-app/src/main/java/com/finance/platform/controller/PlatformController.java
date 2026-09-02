package com.finance.platform.controller;

import com.finance.platform.finance.application.subscription.SubscriptionService;
import com.finance.platform.finance.application.subscription.UsageService;
import com.finance.platform.finance.domain.model.Firm;
import com.finance.platform.finance.domain.model.FirmSubscription;
import com.finance.platform.finance.infrastructure.persistence.FirmJpaRepository;
import com.finance.platform.finance.infrastructure.persistence.FirmSubscriptionJpaRepository;
import com.finance.platform.finance.infrastructure.persistence.UsageQueryRepository;
import com.finance.platform.auth.infrastructure.security.SecurityUtils;
import com.finance.platform.platformadmin.PlatformAdminGrant;
import com.finance.platform.platformadmin.PlatformAdminService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/platform")
@RequiredArgsConstructor
@Tag(name = "Platform administration", description = "SaaS operator tools. Does not expose client financial records.")
public class PlatformController {

	private final PlatformAdminService platformAdminService;
	private final FirmJpaRepository firmRepository;
	private final FirmSubscriptionJpaRepository subscriptionRepository;
	private final SubscriptionService subscriptionService;
	private final UsageService usageService;
	private final UsageQueryRepository usageQueries;

	@GetMapping("/me")
	@PreAuthorize("isAuthenticated()")
	public PlatformAccessView me() {
		return new PlatformAccessView(platformAdminService.isCurrentUserPlatformAdmin());
	}

	@GetMapping("/metrics")
	@PreAuthorize("@platformAdminService.isCurrentUserPlatformAdmin()")
	@Operation(summary = "Aggregate SaaS operational metrics")
	public PlatformMetricsView metrics() {
		return new PlatformMetricsView(
				usageQueries.countAllFirms(),
				usageQueries.countFirmsByStatus("ACTIVE"),
				usageQueries.countFirmsByStatus("TRIAL"),
				usageQueries.countFirmsByStatus("SUSPENDED")
		);
	}

	@GetMapping("/firms")
	@PreAuthorize("@platformAdminService.isCurrentUserPlatformAdmin()")
	public List<PlatformFirmView> firms() {
		return firmRepository.findAll().stream()
				.filter(Firm::isActive)
				.map(firm -> {
					FirmSubscription subscription = subscriptionRepository.findByFirmId(firm.getId()).orElse(null);
					return new PlatformFirmView(
							firm.getId(),
							firm.getName(),
							subscription == null ? null : subscription.getPlanCode(),
							subscription == null ? null : subscription.getStatus().name()
					);
				})
				.toList();
	}

	@GetMapping("/firms/{firmId}")
	@PreAuthorize("@platformAdminService.isCurrentUserPlatformAdmin()")
	public PlatformFirmDetailView firm(@PathVariable UUID firmId) {
		Firm firm = firmRepository.findById(firmId).orElseThrow();
		FirmSubscription subscription = subscriptionRepository.findByFirmId(firmId).orElse(null);
		var usage = usageService.getUsage(firmId);
		return new PlatformFirmDetailView(
				firm.getId(),
				firm.getName(),
				subscription == null ? null : subscription.getPlanCode(),
				subscription == null ? null : subscription.getStatus().name(),
				usage
		);
	}

	@PutMapping("/firms/{firmId}/subscription/plan")
	@PreAuthorize("@platformAdminService.isCurrentUserPlatformAdmin()")
	public PlatformFirmDetailView changePlan(
			@PathVariable UUID firmId,
			@RequestBody ChangePlanRequest body
	) {
		subscriptionService.applyPlan(firmId, body.planCode(), null, body.reason());
		return firm(firmId);
	}

	@PutMapping("/firms/{firmId}/subscription/status")
	@PreAuthorize("@platformAdminService.isCurrentUserPlatformAdmin()")
	public PlatformFirmDetailView changeStatus(
			@PathVariable UUID firmId,
			@RequestBody ChangeStatusRequest body
	) {
		subscriptionService.updateStatus(
				firmId,
				FirmSubscription.SubscriptionStatus.valueOf(body.status().toUpperCase()),
				null,
				body.reason());
		return firm(firmId);
	}

	@PostMapping("/firms/{firmId}/subscription/extend-trial")
	@PreAuthorize("@platformAdminService.isCurrentUserPlatformAdmin()")
	public PlatformFirmDetailView extendTrial(
			@PathVariable UUID firmId,
			@RequestBody ExtendTrialRequest body
	) {
		subscriptionService.extendTrial(firmId, body.additionalDays(), null, body.reason());
		return firm(firmId);
	}

	@PostMapping("/admins/{userId}/grant")
	@PreAuthorize("@platformAdminService.isCurrentUserPlatformAdmin()")
	public PlatformAdminGrantView grantAdmin(@PathVariable UUID userId, @RequestBody AdminGrantRequest body) {
		UUID actor = SecurityUtils.requireCurrentUser().getId();
		var grant = platformAdminService.grant(userId, actor, body.reason());
		return PlatformAdminGrantView.from(grant);
	}

	@PostMapping("/admins/{userId}/revoke")
	@PreAuthorize("@platformAdminService.isCurrentUserPlatformAdmin()")
	public PlatformAdminGrantView revokeAdmin(@PathVariable UUID userId, @RequestBody AdminGrantRequest body) {
		UUID actor = SecurityUtils.requireCurrentUser().getId();
		var grant = platformAdminService.revoke(userId, actor, body.reason());
		return PlatformAdminGrantView.from(grant);
	}

	public record PlatformAccessView(boolean platformAdmin) {
	}

	public record PlatformMetricsView(long totalFirms, long activeSubscriptions, long trialFirms, long suspendedFirms) {
	}

	public record PlatformFirmView(UUID id, String name, String planCode, String status) {
	}

	public record PlatformFirmDetailView(
			UUID id,
			String name,
			String planCode,
			String status,
			com.finance.platform.core.subscription.SubscriptionUsageView usage
	) {
	}

	public record ChangePlanRequest(String planCode, String reason) {
	}

	public record ChangeStatusRequest(String status, String reason) {
	}

	public record ExtendTrialRequest(int additionalDays, String reason) {
	}

	public record AdminGrantRequest(String reason) {
	}

	public record PlatformAdminGrantView(UUID id, UUID userId, boolean active) {
		static PlatformAdminGrantView from(PlatformAdminGrant grant) {
			return new PlatformAdminGrantView(grant.getId(), grant.getUserId(), grant.isActive());
		}
	}
}
