package com.finance.platform.finance.application.dto;

import java.util.UUID;

public record ClientPortfolioItemResponse(
		UUID clientId,
		String clientName,
		UUID primaryAccountantUserId,
		String primaryAccountantName,
		long documentsNeedingReview,
		long bankUnresolved,
		Integer bankReconciliationPercent,
		String closeStatus,
		boolean readyToClose,
		int readinessPercent
) {
}
