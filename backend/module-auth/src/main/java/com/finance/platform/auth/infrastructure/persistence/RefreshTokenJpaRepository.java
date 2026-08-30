package com.finance.platform.auth.infrastructure.persistence;

import com.finance.platform.auth.domain.model.RefreshToken;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface RefreshTokenJpaRepository extends JpaRepository<RefreshToken, UUID> {

	Optional<RefreshToken> findByTokenHash(String tokenHash);

	void deleteByUser_Id(UUID userId);

	java.util.List<RefreshToken> findByUser_IdAndRevokedAtIsNull(UUID userId);
}
