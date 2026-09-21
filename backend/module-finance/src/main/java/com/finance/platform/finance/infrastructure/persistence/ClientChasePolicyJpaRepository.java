package com.finance.platform.finance.infrastructure.persistence;

import com.finance.platform.finance.domain.model.chase.ClientChasePolicy;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ClientChasePolicyJpaRepository extends JpaRepository<ClientChasePolicy, UUID> {

	List<ClientChasePolicy> findByFirmIdAndActiveTrue(UUID firmId);

	Optional<ClientChasePolicy> findByFirmIdAndName(UUID firmId, String name);
}
