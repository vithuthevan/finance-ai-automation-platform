package com.finance.platform.core.notification;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface NotificationJpaRepository extends JpaRepository<Notification, UUID> {

	@Query("""
			select n from Notification n
			where n.userId = :userId
			  and n.firmId = :firmId
			  and (:unreadOnly = false or n.readAt is null)
			  and (:type is null or n.type = :type)
			  and (:clientId is null or n.clientId = :clientId)
			order by n.createdAt desc
			""")
	Page<Notification> search(
			@Param("userId") UUID userId,
			@Param("firmId") UUID firmId,
			@Param("unreadOnly") boolean unreadOnly,
			@Param("type") String type,
			@Param("clientId") UUID clientId,
			Pageable pageable);

	Optional<Notification> findByIdAndUserIdAndFirmId(UUID id, UUID userId, UUID firmId);

	long countByUserIdAndFirmIdAndReadAtIsNull(UUID userId, UUID firmId);

	boolean existsByUserIdAndDedupeKeyAndReadAtIsNull(UUID userId, String dedupeKey);

	@Modifying
	@Query("""
			update Notification n set n.readAt = :readAt
			where n.userId = :userId and n.firmId = :firmId and n.readAt is null
			""")
	int markAllRead(@Param("userId") UUID userId, @Param("firmId") UUID firmId, @Param("readAt") Instant readAt);
}
