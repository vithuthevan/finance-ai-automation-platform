package com.finance.platform.controller;

import com.finance.platform.auth.infrastructure.security.SecurityUtils;
import com.finance.platform.finance.application.dto.chase.ClientChasePolicyResponse;
import com.finance.platform.finance.application.dto.chase.ClientChaseRunResponse;
import com.finance.platform.finance.application.dto.chase.UpsertClientChasePolicyRequest;
import com.finance.platform.finance.application.service.ClientChaseQueryService;
import com.finance.platform.finance.application.service.ClientChaseService;
import com.finance.platform.finance.domain.model.chase.ClientChasePolicy;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/client-chase")
@RequiredArgsConstructor
public class ClientChaseController {

	private final ClientChaseService chaseService;
	private final ClientChaseQueryService chaseQueryService;

	@GetMapping("/policy")
	@PreAuthorize("hasRole('ADMIN')")
	public ClientChasePolicyResponse policy() {
		UUID firmId = SecurityUtils.requireCurrentUser().getFirmId();
		ClientChasePolicy policy = chaseService.defaultPolicy(firmId);
		return new ClientChasePolicyResponse(policy.getId(), policy.getName(), policy.isActive(), policy.getCadenceDays());
	}

	@PutMapping("/policy")
	@PreAuthorize("hasRole('ADMIN')")
	public ClientChasePolicyResponse upsertPolicy(@Valid @RequestBody UpsertClientChasePolicyRequest request) {
		UUID firmId = SecurityUtils.requireCurrentUser().getFirmId();
		ClientChasePolicy policy = chaseService.upsertPolicy(
				firmId, "Default", request.cadenceDays(), request.enabled());
		return new ClientChasePolicyResponse(policy.getId(), policy.getName(), policy.isActive(), policy.getCadenceDays());
	}

	@GetMapping("/runs")
	@PreAuthorize("hasAnyRole('ADMIN', 'ACCOUNTANT')")
	public List<ClientChaseRunResponse> runs() {
		UUID firmId = SecurityUtils.requireCurrentUser().getFirmId();
		return chaseQueryService.listRuns(firmId);
	}

	@PostMapping("/runs/{runId}/suppress")
	@PreAuthorize("hasAnyRole('ADMIN', 'ACCOUNTANT')")
	public void suppress(@org.springframework.web.bind.annotation.PathVariable UUID runId) {
		chaseQueryService.suppress(runId);
	}

	@PostMapping("/runs/{runId}/resume")
	@PreAuthorize("hasAnyRole('ADMIN', 'ACCOUNTANT')")
	public void resume(@org.springframework.web.bind.annotation.PathVariable UUID runId) {
		chaseQueryService.resume(runId);
	}

	@PostMapping("/execute")
	@PreAuthorize("hasRole('ADMIN')")
	public Map<String, Integer> executeNow() {
		UUID firmId = SecurityUtils.requireCurrentUser().getFirmId();
		int dispatched = chaseService.executeDueChaseStepsForFirm(firmId);
		return Map.of("dispatched", dispatched);
	}
}
