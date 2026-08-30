package com.finance.platform.ai.provider;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.finance.platform.ai.application.DocumentClassificationService;
import com.finance.platform.ai.application.DocumentExtractionService;
import com.finance.platform.ai.application.ExtractedDocument;
import com.finance.platform.ai.application.TransactionSuggestionService;
import com.finance.platform.ai.config.AiProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDate;
import java.util.Optional;

@Slf4j
@Component
@RequiredArgsConstructor
public class OpenAiCompatibleExtractionProvider implements DocumentExtractionService, DocumentClassificationService, TransactionSuggestionService {

	private final AiProperties properties;
	private final ObjectMapper objectMapper;
	private final HttpClient httpClient = HttpClient.newBuilder()
			.connectTimeout(Duration.ofSeconds(15))
			.build();

	@Override
	public boolean isEnabled() {
		return properties.isProviderConfigured();
	}

	@Override
	public Optional<ExtractedDocument> extract(byte[] content, String fileName, String mimeType) {
		if (!isEnabled()) {
			return Optional.empty();
		}
		try {
			String body = """
					{
					  "model": "%s",
					  "response_format": {"type":"json_object"},
					  "messages": [
					    {"role":"system","content":"Extract bookkeeping fields from the document metadata. Return JSON with keys: documentType, merchantOrCustomer, invoiceNumber, date, dueDate, currency, subtotal, tax, totalAmount, paymentMethod, description, suggestedTransactionType, candidateCategoryCode, confidence."},
					    {"role":"user","content":"File name: %s. MIME: %s. Size: %d bytes. Infer likely transaction details for a Sri Lankan SME bookkeeping workflow. Use ISO dates and LKR unless another currency is obvious."}
					  ]
					}
					""".formatted(
					escape(properties.getOpenai().getModel()),
					escape(fileName),
					escape(mimeType),
					content == null ? 0 : content.length
			);
			HttpRequest request = HttpRequest.newBuilder()
					.uri(URI.create(trimSlash(properties.getOpenai().getBaseUrl()) + "/chat/completions"))
					.header("Authorization", "Bearer " + properties.getOpenai().getApiKey())
					.header("Content-Type", "application/json")
					.timeout(Duration.ofSeconds(45))
					.POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
					.build();
			HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
			if (response.statusCode() >= 300) {
				log.warn("AI provider returned HTTP {}", response.statusCode());
				return Optional.empty();
			}
			JsonNode root = objectMapper.readTree(response.body());
			String contentJson = root.path("choices").path(0).path("message").path("content").asText();
			if (contentJson == null || contentJson.isBlank()) {
				return Optional.empty();
			}
			JsonNode extracted = objectMapper.readTree(contentJson);
			return Optional.of(toExtracted(extracted, contentJson));
		} catch (Exception ex) {
			log.warn("AI extraction failed: {}", ex.getMessage());
			return Optional.empty();
		}
	}

	@Override
	public Optional<String> classify(ExtractedDocument extracted, String fileName) {
		if (extracted != null && extracted.documentType() != null && !extracted.documentType().isBlank()) {
			return Optional.of(extracted.documentType());
		}
		return Optional.empty();
	}

	@Override
	public ExtractedDocument suggest(ExtractedDocument extracted) {
		return extracted;
	}

	private ExtractedDocument toExtracted(JsonNode node, String raw) {
		return new ExtractedDocument(
				text(node, "documentType"),
				text(node, "merchantOrCustomer"),
				text(node, "invoiceNumber"),
				date(node, "date"),
				date(node, "dueDate"),
				text(node, "currency"),
				decimal(node, "subtotal"),
				decimal(node, "tax"),
				decimal(node, "totalAmount"),
				text(node, "paymentMethod"),
				text(node, "description"),
				text(node, "suggestedTransactionType"),
				text(node, "candidateCategoryCode"),
				decimal(node, "confidence"),
				raw
		);
	}

	private static String text(JsonNode node, String field) {
		JsonNode value = node.get(field);
		return value == null || value.isNull() || value.asText().isBlank() ? null : value.asText();
	}

	private static LocalDate date(JsonNode node, String field) {
		String value = text(node, field);
		if (value == null) {
			return null;
		}
		try {
			return LocalDate.parse(value.substring(0, Math.min(10, value.length())));
		} catch (Exception ex) {
			return null;
		}
	}

	private static BigDecimal decimal(JsonNode node, String field) {
		JsonNode value = node.get(field);
		if (value == null || value.isNull()) {
			return null;
		}
		try {
			return new BigDecimal(value.asText());
		} catch (Exception ex) {
			return null;
		}
	}

	private static String trimSlash(String url) {
		if (url == null) {
			return "";
		}
		return url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
	}

	private static String escape(String value) {
		return value == null ? "" : value.replace("\\", "\\\\").replace("\"", "\\\"");
	}
}
