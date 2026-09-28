package com.finance.platform.core.idempotency;

import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class IdempotencyCleanup {

	private final IdempotencyService idempotencyService;

	@Scheduled(fixedDelayString = "${app.idempotency.cleanup-interval-ms:3600000}")
	public void purgeExpired() {
		idempotencyService.purgeExpired();
	}
}
