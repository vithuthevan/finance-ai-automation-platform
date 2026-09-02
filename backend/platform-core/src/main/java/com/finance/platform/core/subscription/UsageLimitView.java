package com.finance.platform.core.subscription;

public record UsageLimitView(long used, long limit) {

	public int percentUsed() {
		if (limit <= 0) {
			return 0;
		}
		return (int) Math.min(100, Math.round(used * 100.0 / limit));
	}

	public boolean overLimit() {
		return limit > 0 && used > limit;
	}

	public boolean atOrAboveThreshold(int thresholdPercent) {
		return percentUsed() >= thresholdPercent;
	}
}
