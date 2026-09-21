package com.finance.platform.finance.application.service;

import com.finance.platform.auth.infrastructure.security.SecurityUtils;
import com.finance.platform.core.exception.ResourceNotFoundException;
import com.finance.platform.finance.application.dto.chase.ClientChaseRunResponse;
import com.finance.platform.finance.domain.model.chase.ClientChaseAction;
import com.finance.platform.finance.domain.model.chase.ClientChaseRun;
import com.finance.platform.finance.infrastructure.persistence.ClientChaseActionJpaRepository;
import com.finance.platform.finance.infrastructure.persistence.ClientChaseRunJpaRepository;
import com.finance.platform.finance.infrastructure.persistence.ClientJpaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ClientChaseQueryService {

	private final ClientChaseRunJpaRepository runRepository;
	private final ClientChaseActionJpaRepository actionRepository;
	private final ClientJpaRepository clientRepository;

	@Transactional(readOnly = true)
	public List<ClientChaseRunResponse> listRuns(UUID firmId) {
		return runRepository.findByFirmIdOrderByStartedAtDesc(firmId).stream()
				.map(this::toResponse)
				.toList();
	}

	@Transactional
	public void suppress(UUID runId) {
		ClientChaseRun run = requireRun(runId);
		run.setStatus(ClientChaseRun.Status.SUPPRESSED);
		runRepository.save(run);
	}

	@Transactional
	public void resume(UUID runId) {
		ClientChaseRun run = requireRun(runId);
		run.setStatus(ClientChaseRun.Status.ACTIVE);
		runRepository.save(run);
	}

	private ClientChaseRun requireRun(UUID runId) {
		UUID firmId = SecurityUtils.requireCurrentUser().getFirmId();
		return runRepository.findById(runId)
				.filter(run -> run.getFirmId().equals(firmId))
				.orElseThrow(() -> new ResourceNotFoundException("ClientChaseRun", runId));
	}

	private ClientChaseRunResponse toResponse(ClientChaseRun run) {
		String clientName = clientRepository.findById(run.getClientId()).map(c -> c.getName()).orElse("Client");
		List<ClientChaseAction> actions = actionRepository.findByRun_IdOrderBySentAtDesc(run.getId());
		ClientChaseAction last = actions.stream().max(Comparator.comparing(ClientChaseAction::getSentAt)).orElse(null);
		String delivery = last == null ? "NONE" : (last.isSuppressed() ? "UNABLE_TO_SEND" : "SENT");
		return new ClientChaseRunResponse(
				run.getId(),
				run.getClientId(),
				clientName,
				run.getSourceType(),
				run.getSourceId(),
				run.getStatus().name(),
				run.getStartedAt(),
				actions.size(),
				last == null ? null : last.getSentAt(),
				delivery);
	}
}
