package com.finance.platform.reporting.api.impl;

import com.finance.platform.reporting.api.ReportingFacade;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.UUID;

@Service
public class ReportingFacadeImpl implements ReportingFacade {

	@Override
	public PlSummary generatePlSummary(UUID clientId, LocalDate from, LocalDate to) {
		// TODO: aggregate approved transactions via LedgerQueryFacade
		return new PlSummary(clientId, from, to);
	}
}
