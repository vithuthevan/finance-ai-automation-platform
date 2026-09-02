package com.finance.platform.core.notification;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class NotificationPreferenceService {

	private final NotificationPreferenceJpaRepository repository;

	@Transactional(readOnly = true)
	public boolean emailEnabledFor(UUID userId, String type) {
		return repository.findByUserId(userId)
				.map(pref -> pref.allowsEmail(type))
				.orElse(true);
	}

	@Transactional
	public NotificationPreference getOrCreate(UUID firmId, UUID userId) {
		return repository.findByUserId(userId).orElseGet(() -> {
			NotificationPreference pref = new NotificationPreference();
			pref.setFirmId(firmId);
			pref.setUserId(userId);
			return repository.save(pref);
		});
	}

	@Transactional
	public NotificationPreference update(UUID firmId, UUID userId, boolean emailEnabled,
			boolean emailDocumentRequested, boolean emailDocumentUploaded, boolean emailPeriodReady) {
		NotificationPreference pref = getOrCreate(firmId, userId);
		pref.setEmailEnabled(emailEnabled);
		pref.setEmailDocumentRequested(emailDocumentRequested);
		pref.setEmailDocumentUploaded(emailDocumentUploaded);
		pref.setEmailPeriodReady(emailPeriodReady);
		pref.setUpdatedAt(Instant.now());
		return repository.save(pref);
	}
}
