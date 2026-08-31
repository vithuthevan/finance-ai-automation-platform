package com.finance.platform.finance.application.close;

public record CloseFinding(
		CloseCheckSeverity severity,
		String code,
		String message,
		int count,
		String actionHint
) {
	public static CloseFinding blocker(String code, String message, int count, String actionHint) {
		return new CloseFinding(CloseCheckSeverity.BLOCKER, code, message, count, actionHint);
	}

	public static CloseFinding warning(String code, String message, int count, String actionHint) {
		return new CloseFinding(CloseCheckSeverity.WARNING, code, message, count, actionHint);
	}

	public static CloseFinding info(String code, String message, int count, String actionHint) {
		return new CloseFinding(CloseCheckSeverity.INFO, code, message, count, actionHint);
	}
}
