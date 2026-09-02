package com.finance.platform.finance.application.dto;

import com.finance.platform.finance.domain.model.ReconciliationMatch;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record MatchSuggestionResponse(
		UUID matchId,
		String ledgerType,
		UUID ledgerId,
		String ledgerLabel,
		BigDecimal amount,
		LocalDate transactionDate,
		String description,
		int score,
		String confidence,
		ReconciliationMatch.MatchStatus status
) {
}
