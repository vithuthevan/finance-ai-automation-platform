package com.finance.platform.finance.application.close;

public final class CloseResponsibilityResolver {

	private CloseResponsibilityResolver() {
	}

	public static CloseResponsibility forActionHint(String actionHint) {
		if ("DOCUMENT_REQUESTS".equals(actionHint)) {
			return CloseResponsibility.CLIENT;
		}
		return CloseResponsibility.TEAM;
	}

	public static String label(CloseResponsibility responsibility) {
		return responsibility == CloseResponsibility.CLIENT ? "Waiting on client" : "Your team";
	}
}
