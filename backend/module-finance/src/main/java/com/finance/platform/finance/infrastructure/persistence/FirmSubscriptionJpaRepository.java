package com.finance.platform.finance.infrastructure.persistence;

import com.finance.platform.finance.domain.model.FirmSubscription;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface FirmSubscriptionJpaRepository extends JpaRepository<FirmSubscription, UUID> {

	Optional<FirmSubscription> findByFirmId(UUID firmId);
}
