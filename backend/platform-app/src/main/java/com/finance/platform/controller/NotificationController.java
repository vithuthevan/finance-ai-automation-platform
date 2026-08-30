package com.finance.platform.controller;

import com.finance.platform.auth.infrastructure.security.SecurityUtils;
import com.finance.platform.core.dto.PageResponse;
import com.finance.platform.core.notification.Notification;
import com.finance.platform.core.notification.NotificationService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/notifications")
@RequiredArgsConstructor
public class NotificationController {

	private final NotificationService notificationService;

	@GetMapping
	@PreAuthorize("isAuthenticated()")
	public PageResponse<NotificationView> list(
			@RequestParam(defaultValue = "0") int page,
			@RequestParam(defaultValue = "20") int size
	) {
		PageResponse<Notification> results = notificationService.list(SecurityUtils.requireCurrentUser().getId(), page, size);
		return new PageResponse<>(
				results.content().stream().map(NotificationView::from).toList(),
				results.page(),
				results.size(),
				results.totalElements()
		);
	}

	@GetMapping("/unread-count")
	@PreAuthorize("isAuthenticated()")
	public UnreadCount unreadCount() {
		return new UnreadCount(notificationService.unreadCount(SecurityUtils.requireCurrentUser().getId()));
	}

	@PostMapping("/{notificationId}/read")
	@PreAuthorize("isAuthenticated()")
	public NotificationView markRead(@PathVariable UUID notificationId) {
		return NotificationView.from(notificationService.markRead(SecurityUtils.requireCurrentUser().getId(), notificationId));
	}

	public record UnreadCount(long count) {
	}

	public record NotificationView(UUID id, UUID clientId, String type, String title, String message, Instant readAt, Instant createdAt) {
		static NotificationView from(Notification notification) {
			return new NotificationView(
					notification.getId(),
					notification.getClientId(),
					notification.getType(),
					notification.getTitle(),
					notification.getMessage(),
					notification.getReadAt(),
					notification.getCreatedAt()
			);
		}
	}
}
