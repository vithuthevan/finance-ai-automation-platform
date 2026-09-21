package com.finance.platform.finance.application.service;

import com.finance.platform.auth.infrastructure.security.SecurityUtils;
import com.finance.platform.finance.application.dto.PracticeTodayResponse;
import com.finance.platform.finance.application.dto.WorkSummaryResponse;
import com.finance.platform.finance.infrastructure.persistence.SalesInvoiceJpaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.YearMonth;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class PracticeTodayService {

	private final PracticeWorkQueueService practiceWorkQueueService;
	private final MonthEndCommandCenterService monthEndCommandCenterService;
	private final SalesInvoiceJpaRepository salesInvoiceRepository;

	@Transactional(readOnly = true)
	public PracticeTodayResponse today() {
		UUID firmId = SecurityUtils.requireCurrentUser().getFirmId();
		WorkSummaryResponse work = practiceWorkQueueService.summary();
		YearMonth current = YearMonth.now();
		var commandCenter = monthEndCommandCenterService.commandCenter(
				current.getYear(), current.getMonthValue(), null, null, null, null, null);
		long overdueInvoices = salesInvoiceRepository.countOverdue(firmId);
		return new PracticeTodayResponse(
				work.documentsToReview() + work.openDocumentRequests() + work.bankItemsUnresolved(),
				commandCenter.summary().ready(),
				work.openDocumentRequests(),
				work.bankItemsUnresolved(),
				work.documentsToReview(),
				overdueInvoices,
				commandCenter.summary().blocked(),
				commandCenter.summary().needsAttention()
		);
	}
}
