package com.finance.platform.finance.infrastructure.persistence;

import com.finance.platform.finance.domain.model.chase.ClientChaseAction;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface ClientChaseActionJpaRepository extends JpaRepository<ClientChaseAction, UUID> {

	boolean existsByRunIdAndCadenceDayAndChannel(UUID runId, int cadenceDay, String channel);

	java.util.List<ClientChaseAction> findByRun_IdOrderBySentAtDesc(UUID runId);
}
