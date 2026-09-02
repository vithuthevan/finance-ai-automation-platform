package com.finance.platform.finance.application.workflow;

import com.finance.platform.finance.application.dto.PeriodReadinessResponse;
import com.finance.platform.finance.application.event.PeriodReadyToCloseEvent;
import com.finance.platform.finance.application.service.CloseReadinessService;
import com.finance.platform.finance.domain.model.AccountingPeriod;
import com.finance.platform.finance.infrastructure.persistence.AccountingPeriodJpaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class PeriodReadinessNotifier {

	private final CloseReadinessService closeReadinessService;
	private final AccountingPeriodJpaRepository periodRepository;
	private final ApplicationEventPublisher eventPublisher;

	@Transactional(readOnly = true)
	public void checkAndNotify(UUID firmId, UUID clientId) {
		YearMonth current = YearMonth.now();
		AccountingPeriod period = periodRepository
				.findByClient_IdAndPeriodYearAndPeriodMonth(clientId, current.getYear(), current.getMonthValue())
				.orElse(null);
		LocalDate from = current.atDay(1);
		LocalDate to = current.atEndOfMonth();
		UUID periodId = period == null ? null : period.getId();
		if (period != null && period.isClosed()) {
			return;
		}
		PeriodReadinessResponse readiness = closeReadinessService.evaluate(firmId, clientId, periodId, from, to);
		if (readiness.ready() && periodId != null) {
			eventPublisher.publishEvent(new PeriodReadyToCloseEvent(firmId, clientId, periodId));
		}
	}
}
