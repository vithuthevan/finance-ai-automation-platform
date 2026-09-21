package com.finance.platform.finance.application.service;

import com.finance.platform.core.exception.ValidationException;
import com.finance.platform.core.notification.EmailService;
import com.finance.platform.finance.domain.model.Client;
import com.finance.platform.finance.domain.model.DocumentRequest;
import com.finance.platform.finance.domain.model.chase.ClientChaseAction;
import com.finance.platform.finance.domain.model.chase.ClientChasePolicy;
import com.finance.platform.finance.domain.model.chase.ClientChaseRun;
import com.finance.platform.finance.infrastructure.persistence.ClientChaseActionJpaRepository;
import com.finance.platform.finance.infrastructure.persistence.ClientChasePolicyJpaRepository;
import com.finance.platform.finance.infrastructure.persistence.ClientChaseRunJpaRepository;
import com.finance.platform.finance.infrastructure.persistence.ClientJpaRepository;
import com.finance.platform.finance.infrastructure.persistence.DocumentRequestJpaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ClientChaseService {

	public static final String SOURCE_DOCUMENT_REQUEST = "DOCUMENT_REQUEST";

	private final ClientChasePolicyJpaRepository policyRepository;
	private final ClientChaseRunJpaRepository runRepository;
	private final ClientChaseActionJpaRepository actionRepository;
	private final DocumentRequestJpaRepository documentRequestRepository;
	private final ClientJpaRepository clientRepository;
	private final PracticeWorkAssignmentService practiceWorkAssignmentService;
	private final EmailService emailService;

	@Transactional
	public void ensureRunsForOpenDocumentRequests(UUID firmId) {
		ClientChasePolicy policy = defaultPolicy(firmId);
		List<DocumentRequest> open = documentRequestRepository.findOpenByFirmId(firmId);
		for (DocumentRequest request : open) {
			if (runRepository.existsByFirmIdAndSourceTypeAndSourceId(firmId, SOURCE_DOCUMENT_REQUEST, request.getId())) {
				continue;
			}
			ClientChaseRun run = ClientChaseRun.builder()
					.firmId(firmId)
					.clientId(request.getClient().getId())
					.policyId(policy.getId())
					.sourceType(SOURCE_DOCUMENT_REQUEST)
					.sourceId(request.getId())
					.status(ClientChaseRun.Status.ACTIVE)
					.build();
			runRepository.save(run);
		}
		completeRunsForResolvedSources(firmId);
	}

	@Transactional(readOnly = true)
	public Optional<String> resolveClientContactEmail(UUID clientId) {
		return clientRepository.findById(clientId)
				.map(Client::getContactEmail)
				.filter(email -> email != null && !email.isBlank());
	}

	@Transactional
	public ClientChasePolicy defaultPolicy(UUID firmId) {
		return policyRepository.findByFirmIdAndName(firmId, "Default")
				.orElseGet(() -> policyRepository.save(ClientChasePolicy.builder()
						.firmId(firmId)
						.name("Default")
						.active(true)
						.cadenceDays(new int[] {0, 3, 7, 10})
						.build()));
	}

	@Transactional
	public ClientChasePolicy upsertPolicy(UUID firmId, String name, int[] cadenceDays, boolean active) {
		if (cadenceDays == null || cadenceDays.length == 0) {
			throw new ValidationException("cadenceDays", "At least one cadence day is required");
		}
		ClientChasePolicy policy = policyRepository.findByFirmIdAndName(firmId, name)
				.orElse(ClientChasePolicy.builder().firmId(firmId).name(name).build());
		policy.setCadenceDays(cadenceDays);
		policy.setActive(active);
		return policyRepository.save(policy);
	}

	@Transactional
	public int executeDueChaseStepsForFirm(UUID firmId) {
		ensureRunsForOpenDocumentRequests(firmId);
		ClientChasePolicy policy = defaultPolicy(firmId);
		if (!policy.isActive()) {
			return 0;
		}
		int dispatched = 0;
		List<ClientChaseRun> activeRuns = runRepository.findByFirmIdAndStatus(firmId, ClientChaseRun.Status.ACTIVE);
		for (ClientChaseRun run : activeRuns) {
			dispatched += dispatchRun(policy, run);
		}
		return dispatched;
	}

	private int dispatchRun(ClientChasePolicy policy, ClientChaseRun run) {
		long daysOpen = ChronoUnit.DAYS.between(run.getStartedAt(), Instant.now());
		int dispatched = 0;
		for (int cadenceDay : policy.getCadenceDays()) {
			if (daysOpen < cadenceDay) {
				continue;
			}
			if (actionRepository.existsByRunIdAndCadenceDayAndChannel(run.getId(), cadenceDay, "EMAIL")) {
				continue;
			}
			Optional<String> recipient = resolveClientContactEmail(run.getClientId());
			String subject = "Reminder: documents needed for your accounts";
			String body = "This is reminder step day " + cadenceDay + " for outstanding items your accountant requested.";
			boolean suppressed = recipient.isEmpty();
			if (!suppressed) {
				emailService.send(recipient.get(), subject, body);
			} else {
				recordUndeliverableChase(
						run.getFirmId(),
						run.getClientId(),
						run.getId(),
						"No client email configured");
			}
			actionRepository.save(ClientChaseAction.builder()
					.firmId(run.getFirmId())
					.run(run)
					.channel("EMAIL")
					.cadenceDay(cadenceDay)
					.recipient(recipient.orElse(null))
					.subject(subject)
					.messageSummary(suppressed ? "Unable to send: no client email configured" : body)
					.suppressed(suppressed)
					.build());
			dispatched++;
		}
		return dispatched;
	}

	private void completeRunsForResolvedSources(UUID firmId) {
		List<ClientChaseRun> active = runRepository.findByFirmIdAndStatus(firmId, ClientChaseRun.Status.ACTIVE);
		for (ClientChaseRun run : active) {
			if (!SOURCE_DOCUMENT_REQUEST.equals(run.getSourceType())) {
				continue;
			}
			boolean stillOpen = documentRequestRepository.findById(run.getSourceId())
					.filter(request -> request.getClient().getFirmId().equals(firmId))
					.map(request -> request.getStatus() == DocumentRequest.RequestStatus.OPEN
							|| request.getStatus() == DocumentRequest.RequestStatus.UPLOADED)
					.orElse(false);
			if (!stillOpen) {
				run.setStatus(ClientChaseRun.Status.COMPLETED);
				runRepository.save(run);
			}
		}
	}

	public void recordUndeliverableChase(UUID firmId, UUID clientId, UUID runId, String reason) {
		practiceWorkAssignmentService.ensureOpen(
				firmId,
				clientId,
				"CLIENT_CONTACT_REQUIRED",
				runId,
				reason);
	}
}
