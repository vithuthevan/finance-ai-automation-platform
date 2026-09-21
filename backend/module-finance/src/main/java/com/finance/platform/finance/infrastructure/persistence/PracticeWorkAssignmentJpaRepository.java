package com.finance.platform.finance.infrastructure.persistence;

import com.finance.platform.finance.domain.model.PracticeWorkAssignment;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface PracticeWorkAssignmentJpaRepository extends JpaRepository<PracticeWorkAssignment, UUID> {

	Optional<PracticeWorkAssignment> findByFirmIdAndSourceTypeAndSourceId(UUID firmId, String sourceType, UUID sourceId);

	Optional<PracticeWorkAssignment> findByIdAndFirmId(UUID id, UUID firmId);
}
