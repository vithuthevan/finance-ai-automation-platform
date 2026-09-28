package com.finance.platform.auth.infrastructure.persistence;

import com.finance.platform.auth.domain.model.RefreshToken;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface RefreshTokenJpaRepository extends JpaRepository<RefreshToken, UUID> {

	Optional<RefreshToken> findByTokenHash(String tokenHash);

	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select r from RefreshToken r join fetch r.user where r.tokenHash = :tokenHash")
	Optional<RefreshToken> findByTokenHashForUpdate(@Param("tokenHash") String tokenHash);

	void deleteByUser_Id(UUID userId);

	java.util.List<RefreshToken> findByUser_IdAndRevokedAtIsNull(UUID userId);

	@Modifying
	@Query("""
			update RefreshToken r set r.revokedAt = :revokedAt
			where r.user.id = :userId and r.revokedAt is null
			""")
	int revokeAllActiveForUser(@Param("userId") UUID userId, @Param("revokedAt") Instant revokedAt);
}
