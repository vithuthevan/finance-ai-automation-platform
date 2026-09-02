package com.finance.platform.platformadmin;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface PlatformAdminGrantJpaRepository extends JpaRepository<PlatformAdminGrant, UUID> {

	Optional<PlatformAdminGrant> findByUserId(UUID userId);

	boolean existsByUserIdAndActiveTrue(UUID userId);
}
