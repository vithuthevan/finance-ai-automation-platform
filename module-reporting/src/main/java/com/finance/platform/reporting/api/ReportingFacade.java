package com.finance.platform.reporting.api;

import java.time.LocalDate;
import java.util.UUID;

public interface ReportingFacade {

	PlSummary generatePlSummary(UUID clientId, LocalDate from, LocalDate to);

	record PlSummary(UUID clientId, LocalDate from, LocalDate to) {
	}
}
