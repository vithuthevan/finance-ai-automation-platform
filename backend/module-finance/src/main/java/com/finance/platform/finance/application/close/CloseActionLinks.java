package com.finance.platform.finance.application.close;

import com.finance.platform.finance.application.dto.CloseActionLinkResponse;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Maps close-readiness action hints to navigable UI paths (frontend routes under /app).
 */
public final class CloseActionLinks {

	private CloseActionLinks() {
	}

	public static CloseActionLinkResponse resolve(
			String actionHint,
			UUID clientId,
			UUID periodId,
			LocalDate from,
			LocalDate to
	) {
		if (actionHint == null || actionHint.isBlank()) {
			return genericDocuments(clientId, from, to);
		}
		return switch (actionHint) {
			case "EXPENSES_DRAFT", "TRANSACTIONS_UNSUPPORTED" -> link(
					"Review transactions",
					"/app/expenses",
					query(clientId, from, to, Map.of("status", actionHint.equals("EXPENSES_DRAFT") ? "DRAFT" : "APPROVED")));
			case "INCOME_DRAFT" -> link(
					"Review transactions",
					"/app/income",
					query(clientId, from, to, Map.of("status", "DRAFT")));
			case "DOCUMENT_REQUESTS" -> link(
					"View requests",
					periodId != null ? "/app/close/" + clientId + "/" + periodId : "/app/requests",
					Map.of());
			case "DOCUMENTS_REVIEW" -> link(
					"Review documents",
					"/app/documents",
					query(clientId, from, to, Map.of("status", "NEEDS_REVIEW")));
			case "DOCUMENTS_FAILED" -> link(
					"Review documents",
					"/app/documents",
					query(clientId, from, to, Map.of("status", "FAILED")));
			case "DOCUMENTS_UNLINKED" -> link(
					"Link documents",
					"/app/documents",
					query(clientId, from, to, Map.of("linked", "false")));
			case "BANKING", "BANKING_UNMATCHED", "BANKING_SUGGESTED", "BANKING_PENDING" -> link(
					"Reconcile now",
					"/app/banking",
					query(clientId, from, to, Map.of()));
			default -> genericDocuments(clientId, from, to);
		};
	}

	private static CloseActionLinkResponse genericDocuments(UUID clientId, LocalDate from, LocalDate to) {
		return link("Open workflow", "/app/documents", query(clientId, from, to, Map.of()));
	}

	private static CloseActionLinkResponse link(String label, String path, Map<String, String> query) {
		return new CloseActionLinkResponse(label, path, query);
	}

	private static Map<String, String> query(UUID clientId, LocalDate from, LocalDate to, Map<String, String> extra) {
		Map<String, String> q = new LinkedHashMap<>();
		if (clientId != null) {
			q.put("clientId", clientId.toString());
		}
		if (from != null) {
			q.put("from", from.toString());
		}
		if (to != null) {
			q.put("to", to.toString());
		}
		q.putAll(extra);
		return q;
	}
}
