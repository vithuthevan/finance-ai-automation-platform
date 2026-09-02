package com.finance.platform.core.notification;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class EmailTemplateService {

	@Value("${app.frontend.base-url:}")
	private String frontendBaseUrl;

	public String documentRequested(String clientName, String description, String dueDate) {
		return render("Document requested",
				"Your accountant needs a document for " + safe(clientName) + ".",
				"Request: " + safe(description) + (dueDate == null ? "" : "\nDue: " + dueDate),
				"/app/owner");
	}

	public String documentUploaded(String clientName, String description) {
		return render("Document received",
				"A requested document was uploaded for " + safe(clientName) + ".",
				safe(description),
				"/app/documents");
	}

	public String periodReady(String clientName, String periodLabel) {
		return render("Period ready to close",
				clientName + " — " + periodLabel + " has no close blockers.",
				"Review and close when you are satisfied with the books.",
				"/app/close");
	}

	public String periodClosed(String clientName, String periodLabel) {
		return render("Bookkeeping period closed",
				"Your accountant has finalized bookkeeping for " + safe(clientName) + " (" + periodLabel + ").",
				"This is not an audited financial statement.",
				"/app/owner");
	}

	public String documentReviewNeeded(String clientName, String description) {
		return render("Document needs review",
				"A new document for " + safe(clientName) + " needs your review.",
				safe(description),
				"/app/documents");
	}

	private String render(String subject, String intro, String detail, String path) {
		String link = absolute(path);
		return subject + "\n\n" + intro + "\n" + detail + (link.isBlank() ? "" : "\n\nOpen: " + link);
	}

	public String absolute(String relativePath) {
		if (relativePath == null || relativePath.isBlank()) {
			return "";
		}
		if (frontendBaseUrl == null || frontendBaseUrl.isBlank()) {
			return relativePath;
		}
		String base = frontendBaseUrl.endsWith("/") ? frontendBaseUrl.substring(0, frontendBaseUrl.length() - 1) : frontendBaseUrl;
		return base + (relativePath.startsWith("/") ? relativePath : "/" + relativePath);
	}

	private static String safe(String value) {
		return value == null ? "" : value.replaceAll("[\\r\\n]", " ").trim();
	}
}
