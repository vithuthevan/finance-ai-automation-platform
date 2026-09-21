package com.finance.platform.finance.application.invoicing;

import java.math.BigDecimal;
import java.math.RoundingMode;

public final class MoneyMath {

	public static final int SCALE = 4;
	public static final RoundingMode ROUNDING = RoundingMode.HALF_UP;

	private MoneyMath() {
	}

	public static BigDecimal money(BigDecimal value) {
		if (value == null) {
			return BigDecimal.ZERO.setScale(SCALE, ROUNDING);
		}
		return value.setScale(SCALE, ROUNDING);
	}

	public static BigDecimal multiply(BigDecimal a, BigDecimal b) {
		return money(a.multiply(b));
	}

	public static BigDecimal add(BigDecimal a, BigDecimal b) {
		return money(a.add(b));
	}

	public static BigDecimal subtract(BigDecimal a, BigDecimal b) {
		return money(a.subtract(b));
	}
}
