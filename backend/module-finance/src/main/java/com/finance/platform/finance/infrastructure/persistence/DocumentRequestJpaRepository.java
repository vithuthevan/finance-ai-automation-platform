package com.finance.platform.finance.infrastructure.persistence;

import com.finance.platform.finance.domain.model.DocumentRequest;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface DocumentRequestJpaRepository extends JpaRepository<DocumentRequest, UUID> {

	List<DocumentRequest> findByClient_IdOrderByCreatedAtDesc(UUID clientId);

	List<DocumentRequest> findByAssigneeUserIdAndStatus(UUID assigneeUserId, DocumentRequest.RequestStatus status);

	Optional<DocumentRequest> findByIdAndClient_Id(UUID id, UUID clientId);

	long countByClient_IdAndStatus(UUID clientId, DocumentRequest.RequestStatus status);
}
