package com.finance.platform.auth.infrastructure.persistence;

import com.finance.platform.auth.domain.model.PasswordResetToken;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface PasswordResetTokenJpaRepository extends JpaRepository<PasswordResetToken, UUID> {

	Optional<PasswordResetToken> findByTokenHash(String tokenHash);

	java.util.List<PasswordResetToken> findByUser_IdAndUsedAtIsNull(UUID userId);
}
