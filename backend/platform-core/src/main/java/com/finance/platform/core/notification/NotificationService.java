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
	public PageResponse<Notification> list(UUID userId, int page, int size) {
		Page<Notification> results = notificationRepository.findByUserIdOrderByCreatedAtDesc(userId, PageRequest.of(page, size));
		return new PageResponse<>(results.getContent(), results.getNumber(), results.getSize(), results.getTotalElements());
	}

	@Transactional
	public Notification markRead(UUID userId, UUID notificationId) {
		Notification notification = notificationRepository.findByIdAndUserId(notificationId, userId)
				.orElseThrow(() -> new ResourceNotFoundException("Notification", notificationId));
		notification.setReadAt(Instant.now());
		return notificationRepository.save(notification);
	}

	@Transactional(readOnly = true)
	public long unreadCount(UUID userId) {
		return notificationRepository.countByUserIdAndReadAtIsNull(userId);
	}
}
