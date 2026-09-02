package com.finance.platform.core.notification;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface NotificationDeliveryJpaRepository extends JpaRepository<NotificationDelivery, UUID> {
}
