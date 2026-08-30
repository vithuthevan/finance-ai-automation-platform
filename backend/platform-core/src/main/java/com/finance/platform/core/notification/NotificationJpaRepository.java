package com.finance.platform.core.notification;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface NotificationJpaRepository extends JpaRepository<Notification, UUID> {

	Page<Notification> findByUserIdOrderByCreatedAtDesc(UUID userId, Pageable pageable);

	Optional<Notification> findByIdAndUserId(UUID id, UUID userId);

	long countByUserIdAndReadAtIsNull(UUID userId);
}
