package com.finance.platform.finance.application.banking;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ReconciliationProgressPercentTest {

	@Test
	void threeMatchedOneIgnored_is100Percent() {
		assertEquals(100, ReconciliationProgressPercent.compute(4, 3, 1));
	}

	@Test
	void threeMatchedOneUnmatched_is75Percent() {
		assertEquals(75, ReconciliationProgressPercent.compute(4, 3, 0));
	}

	@Test
	void threeMatchedOneSuggested_treatedAsUnresolved_is75Percent() {
		assertEquals(75, ReconciliationProgressPercent.compute(4, 3, 0));
	}

	@Test
	void fourMatched_is100Percent() {
		assertEquals(100, ReconciliationProgressPercent.compute(4, 4, 0));
	}

	@Test
	void fourIgnored_is100Percent() {
		assertEquals(100, ReconciliationProgressPercent.compute(4, 0, 4));
	}

	@Test
	void emptyScope_is100Percent() {
		assertEquals(100, ReconciliationProgressPercent.compute(0, 0, 0));
	}

}
