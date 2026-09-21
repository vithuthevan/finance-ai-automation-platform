package com.finance.platform.finance.application.service;

import com.finance.platform.auth.infrastructure.security.SecurityUtils;
import com.finance.platform.core.exception.ResourceNotFoundException;
import com.finance.platform.finance.application.dto.AssignPracticeWorkRequest;
import com.finance.platform.finance.application.dto.PracticeWorkAssignmentResponse;
import com.finance.platform.finance.domain.model.PracticeWorkAssignment;
import com.finance.platform.finance.infrastructure.persistence.PracticeWorkAssignmentJpaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class PracticeWorkAssignmentService {

	private final PracticeWorkAssignmentJpaRepository assignmentRepository;

	@Transactional
	public PracticeWorkAssignmentResponse assign(AssignPracticeWorkRequest request) {
		UUID firmId = SecurityUtils.requireCurrentUser().getFirmId();
		PracticeWorkAssignment assignment = assignmentRepository
				.findByFirmIdAndSourceTypeAndSourceId(firmId, request.sourceType(), request.sourceId())
				.orElse(PracticeWorkAssignment.builder()
						.clientId(request.clientId())
						.sourceType(request.sourceType())
						.sourceId(request.sourceId())
						.build());
		assignment.setFirmId(firmId);
		assignment.setAssignedUserId(request.assignedUserId());
		assignment.setDueDate(request.dueDate());
		if (assignment.getStatus() == null) {
			assignment.setStatus(PracticeWorkAssignment.Status.OPEN);
		}
		assignment = assignmentRepository.save(assignment);
		return toResponse(assignment);
	}

	@Transactional
	public void ensureOpen(UUID firmId, UUID clientId, String sourceType, UUID sourceId, String notes) {
		assignmentRepository.findByFirmIdAndSourceTypeAndSourceId(firmId, sourceType, sourceId)
				.filter(row -> row.getStatus() == PracticeWorkAssignment.Status.OPEN
						|| row.getStatus() == PracticeWorkAssignment.Status.IN_PROGRESS)
				.ifPresentOrElse(
						existing -> {
						},
						() -> {
							PracticeWorkAssignment assignment = PracticeWorkAssignment.builder()
									.clientId(clientId)
									.sourceType(sourceType)
									.sourceId(sourceId)
									.status(PracticeWorkAssignment.Status.OPEN)
									.build();
							assignment.setFirmId(firmId);
							assignmentRepository.save(assignment);
						});
	}

	@Transactional
	public PracticeWorkAssignmentResponse clearAssignment(UUID assignmentId) {
		PracticeWorkAssignment assignment = require(assignmentId);
		assignment.setAssignedUserId(null);
		assignment = assignmentRepository.save(assignment);
		return toResponse(assignment);
	}

	@Transactional
	public PracticeWorkAssignmentResponse complete(UUID assignmentId) {
		PracticeWorkAssignment assignment = require(assignmentId);
		assignment.setStatus(PracticeWorkAssignment.Status.COMPLETED);
		assignment = assignmentRepository.save(assignment);
		return toResponse(assignment);
	}

	private PracticeWorkAssignment require(UUID assignmentId) {
		UUID firmId = SecurityUtils.requireCurrentUser().getFirmId();
		return assignmentRepository.findByIdAndFirmId(assignmentId, firmId)
				.orElseThrow(() -> new ResourceNotFoundException("PracticeWorkAssignment", assignmentId));
	}

	private static PracticeWorkAssignmentResponse toResponse(PracticeWorkAssignment assignment) {
		return new PracticeWorkAssignmentResponse(
				assignment.getId(),
				assignment.getClientId(),
				assignment.getSourceType(),
				assignment.getSourceId(),
				assignment.getAssignedUserId(),
				assignment.getStatus().name(),
				assignment.getDueDate());
	}
}
