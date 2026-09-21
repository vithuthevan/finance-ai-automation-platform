package com.finance.platform.finance.infrastructure.persistence;

import com.finance.platform.finance.domain.model.chase.ClientChaseRun;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface ClientChaseRunJpaRepository extends JpaRepository<ClientChaseRun, UUID> {

	List<ClientChaseRun> findByStatus(ClientChaseRun.Status status);

	boolean existsByFirmIdAndSourceTypeAndSourceId(UUID firmId, String sourceType, UUID sourceId);

	List<ClientChaseRun> findByFirmIdAndStatus(UUID firmId, ClientChaseRun.Status status);

	List<ClientChaseRun> findByFirmIdOrderByStartedAtDesc(UUID firmId);
}
