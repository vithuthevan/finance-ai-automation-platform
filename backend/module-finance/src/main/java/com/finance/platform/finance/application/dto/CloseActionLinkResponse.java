package com.finance.platform.finance.application.dto;

import java.util.Map;

public record CloseActionLinkResponse(
		String label,
		String path,
		Map<String, String> query
) {
}
