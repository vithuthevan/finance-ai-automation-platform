package com.finance.platform.core.idempotency;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface IdempotencyKeyJpaRepository extends JpaRepository<IdempotencyKey, UUID> {

	Optional<IdempotencyKey> findByFirmIdAndUserIdAndKeyHash(UUID firmId, UUID userId, String keyHash);
}
