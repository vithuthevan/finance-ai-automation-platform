package com.finance.platform.core.notification;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface NotificationPreferenceJpaRepository extends JpaRepository<NotificationPreference, UUID> {

	Optional<NotificationPreference> findByUserId(UUID userId);
}
