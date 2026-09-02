package com.finance.platform.finance.application.dto;

import java.util.UUID;

public record StaffWorkloadItemResponse(
		UUID userId,
		String fullName,
		long documentsToReview,
		long pendingApprovals,
		long bankUnresolved,
		long closeReady
) {
}
