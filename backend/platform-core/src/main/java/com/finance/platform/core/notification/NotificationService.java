package com.finance.platform.core.notification;

import com.finance.platform.core.dto.PageResponse;
import com.finance.platform.core.exception.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class NotificationService {

	private final NotificationJpaRepository notificationRepository;

	@Transactional(readOnly = true)
	public PageResponse<Notification> list(UUID firmId, UUID userId, boolean unreadOnly, String type, UUID clientId, int page, int size) {
		int capped = Math.min(Math.max(size, 1), 100);
		Page<Notification> results = notificationRepository.search(
				userId, firmId, unreadOnly, blankToNull(type), clientId, PageRequest.of(page, capped));
		return new PageResponse<>(results.getContent(), results.getNumber(), results.getSize(), results.getTotalElements());
	}

	@Transactional
	public Notification markRead(UUID firmId, UUID userId, UUID notificationId) {
		Notification notification = notificationRepository.findByIdAndUserIdAndFirmId(notificationId, userId, firmId)
				.orElseThrow(() -> new ResourceNotFoundException("Notification", notificationId));
		if (notification.getReadAt() == null) {
			notification.setReadAt(Instant.now());
			notificationRepository.save(notification);
		}
		return notification;
	}

	@Transactional
	public int markAllRead(UUID firmId, UUID userId) {
		return notificationRepository.markAllRead(userId, firmId, Instant.now());
	}

	@Transactional(readOnly = true)
	public long unreadCount(UUID firmId, UUID userId) {
		return notificationRepository.countByUserIdAndFirmIdAndReadAtIsNull(userId, firmId);
	}

	private static String blankToNull(String value) {
		return value == null || value.isBlank() ? null : value.trim();
	}
}
