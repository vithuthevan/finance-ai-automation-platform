package com.finance.platform.finance.application.dto;

import java.util.List;

public record PeriodReadinessResponse(
		boolean ready,
		int readinessPercent,
		List<CloseFindingResponse> blockers,
		List<CloseFindingResponse> warnings,
		List<CloseFindingResponse> info,
		List<CloseChecklistItemResponse> checklist,
		PeriodReadinessSummaryResponse summary,
		PeriodLedgerSummaryResponse ledger,
		PeriodDocumentSummaryResponse documents
) {
}
