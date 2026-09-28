package com.finance.platform.core.idempotency;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface IdempotencyKeyJpaRepository extends JpaRepository<IdempotencyKey, UUID> {

	Optional<IdempotencyKey> findByFirmIdAndUserIdAndKeyHash(UUID firmId, UUID userId, String keyHash);

	@Modifying(clearAutomatically = true, flushAutomatically = true)
	@Query("""
			delete from IdempotencyKey k
			where k.firmId = :firmId and k.userId = :userId and k.keyHash = :keyHash
			  and k.status = :status and k.createdAt < :cutoff
			""")
	int deleteScopedBefore(
			@Param("firmId") UUID firmId,
			@Param("userId") UUID userId,
			@Param("keyHash") String keyHash,
			@Param("status") IdempotencyKey.Status status,
			@Param("cutoff") Instant cutoff
	);

	@Modifying(clearAutomatically = true, flushAutomatically = true)
	@Query("delete from IdempotencyKey k where k.id = :id and k.status = :status")
	int deleteByIdAndStatus(@Param("id") UUID id, @Param("status") IdempotencyKey.Status status);

	@Modifying(clearAutomatically = true, flushAutomatically = true)
	@Query("delete from IdempotencyKey k where k.createdAt < :cutoff")
	int deleteCreatedBefore(@Param("cutoff") Instant cutoff);
}
