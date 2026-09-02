package com.finance.platform.controller;

import com.finance.platform.auth.infrastructure.security.SecurityUtils;
import com.finance.platform.core.dto.PageResponse;
import com.finance.platform.core.notification.Notification;
import com.finance.platform.core.notification.NotificationPreference;
import com.finance.platform.core.notification.NotificationPreferenceService;
import com.finance.platform.core.notification.NotificationService;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/notifications")
@RequiredArgsConstructor
@Tag(name = "Notifications", description = "In-app notifications for the authenticated user.")
public class NotificationController {

	private final NotificationService notificationService;
	private final NotificationPreferenceService preferenceService;

	@GetMapping
	@PreAuthorize("isAuthenticated()")
	public PageResponse<NotificationView> list(
			@RequestParam(defaultValue = "0") int page,
			@RequestParam(defaultValue = "20") int size,
			@RequestParam(defaultValue = "false") boolean unreadOnly,
			@RequestParam(required = false) String type,
			@RequestParam(required = false) UUID clientId
	) {
		var user = SecurityUtils.requireCurrentUser();
		PageResponse<Notification> results = notificationService.list(
				user.getFirmId(), user.getId(), unreadOnly, type, clientId, page, size);
		return new PageResponse<>(
				results.content().stream().map(NotificationView::from).toList(),
				results.page(),
				results.size(),
				results.totalElements());
	}

	@GetMapping("/unread-count")
	@PreAuthorize("isAuthenticated()")
	public UnreadCount unreadCount() {
		var user = SecurityUtils.requireCurrentUser();
		return new UnreadCount(notificationService.unreadCount(user.getFirmId(), user.getId()));
	}

	@PostMapping("/{notificationId}/read")
	@PreAuthorize("isAuthenticated()")
	public NotificationView markRead(@PathVariable UUID notificationId) {
		var user = SecurityUtils.requireCurrentUser();
		return NotificationView.from(notificationService.markRead(user.getFirmId(), user.getId(), notificationId));
	}

	@PostMapping("/read-all")
	@PreAuthorize("isAuthenticated()")
	public MarkAllReadResponse markAllRead() {
		var user = SecurityUtils.requireCurrentUser();
		return new MarkAllReadResponse(notificationService.markAllRead(user.getFirmId(), user.getId()));
	}

	@GetMapping("/preferences")
	@PreAuthorize("isAuthenticated()")
	public PreferenceView preferences() {
		var user = SecurityUtils.requireCurrentUser();
		NotificationPreference pref = preferenceService.getOrCreate(user.getFirmId(), user.getId());
		return PreferenceView.from(pref);
	}

	@PutMapping("/preferences")
	@PreAuthorize("isAuthenticated()")
	public PreferenceView updatePreferences(@RequestBody PreferenceUpdate body) {
		var user = SecurityUtils.requireCurrentUser();
		return PreferenceView.from(preferenceService.update(
				user.getFirmId(), user.getId(), body.emailEnabled(), body.emailDocumentRequested(),
				body.emailDocumentUploaded(), body.emailPeriodReady()));
	}

	public record UnreadCount(long count) {
	}

	public record MarkAllReadResponse(int updated) {
	}

	public record PreferenceUpdate(
			boolean emailEnabled,
			boolean emailDocumentRequested,
			boolean emailDocumentUploaded,
			boolean emailPeriodReady
	) {
	}

	public record PreferenceView(
			boolean emailEnabled,
			boolean emailDocumentRequested,
			boolean emailDocumentUploaded,
			boolean emailPeriodReady
	) {
		static PreferenceView from(NotificationPreference pref) {
			return new PreferenceView(
					pref.isEmailEnabled(),
					pref.isEmailDocumentRequested(),
					pref.isEmailDocumentUploaded(),
					pref.isEmailPeriodReady());
		}
	}

	public record NotificationView(
			UUID id,
			UUID clientId,
			String type,
			String title,
			String message,
			String resourceType,
			UUID resourceId,
			String actionUrl,
			boolean read,
			Instant readAt,
			Instant createdAt
	) {
		static NotificationView from(Notification notification) {
			return new NotificationView(
					notification.getId(),
					notification.getClientId(),
					notification.getType(),
					notification.getTitle(),
					notification.getMessage(),
					notification.getResourceType(),
					notification.getResourceId(),
					notification.getActionUrl(),
					notification.isRead(),
					notification.getReadAt(),
					notification.getCreatedAt());
		}
	}
}
