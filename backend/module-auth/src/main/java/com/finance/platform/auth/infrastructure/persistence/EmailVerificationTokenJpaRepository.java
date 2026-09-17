package com.finance.platform.auth.infrastructure.persistence;

import com.finance.platform.auth.domain.model.EmailVerificationToken;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface EmailVerificationTokenJpaRepository extends JpaRepository<EmailVerificationToken, UUID> {

	Optional<EmailVerificationToken> findByTokenHash(String tokenHash);

	List<EmailVerificationToken> findByUser_IdAndUsedAtIsNull(UUID userId);
}
