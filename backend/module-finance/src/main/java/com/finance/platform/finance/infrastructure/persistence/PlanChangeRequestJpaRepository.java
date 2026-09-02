package com.finance.platform.finance.infrastructure.persistence;

import com.finance.platform.finance.domain.model.PlanChangeRequest;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface PlanChangeRequestJpaRepository extends JpaRepository<PlanChangeRequest, UUID> {

	List<PlanChangeRequest> findByFirmIdAndStatusOrderByCreatedAtDesc(UUID firmId, PlanChangeRequest.RequestStatus status);
}
