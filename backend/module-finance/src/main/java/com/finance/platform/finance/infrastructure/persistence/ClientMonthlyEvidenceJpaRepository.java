package com.finance.platform.finance.infrastructure.persistence;

import com.finance.platform.finance.domain.model.ClientMonthlyEvidenceItem;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface ClientMonthlyEvidenceJpaRepository extends JpaRepository<ClientMonthlyEvidenceItem, UUID> {

	List<ClientMonthlyEvidenceItem> findByClient_IdAndActiveTrueOrderBySortOrderAscTitleAsc(UUID clientId);

	long countByClient_IdAndActiveTrue(UUID clientId);

	long countByFirmIdAndActiveTrue(UUID firmId);
}
