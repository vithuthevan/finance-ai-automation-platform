package com.finance.platform.finance.application.close;

import java.time.LocalDate;
import java.util.UUID;

public record CloseCheckContext(
		UUID firmId,
		UUID clientId,
		UUID periodId,
		LocalDate from,
		LocalDate to
) {
}
