package com.finance.platform.finance.application.dto.invoicing;

import java.math.BigDecimal;

public record ArAgeingBuckets(
		BigDecimal current,
		BigDecimal days1To30,
		BigDecimal days31To60,
		BigDecimal days61To90,
		BigDecimal days90Plus
) {
}
