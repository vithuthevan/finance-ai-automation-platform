package com.finance.platform.finance.infrastructure.persistence;

import com.finance.platform.finance.domain.model.FirmSubscription;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface FirmSubscriptionJpaRepository extends JpaRepository<FirmSubscription, UUID> {

	Optional<FirmSubscription> findByFirmId(UUID firmId);

	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("SELECT s FROM FirmSubscription s WHERE s.firmId = :firmId")
	Optional<FirmSubscription> findByFirmIdForUpdate(@Param("firmId") UUID firmId);
}
