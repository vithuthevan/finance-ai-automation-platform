package com.finance.platform.ai.provider;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.finance.platform.ai.application.AccountingSuggestionPromptBuilder;
import com.finance.platform.ai.application.DocumentClassificationService;
import com.finance.platform.ai.application.DocumentExtractionService;
import com.finance.platform.ai.application.ExtractedDocument;
import com.finance.platform.ai.application.ExtractionPromptBuilder;
import com.finance.platform.ai.application.TransactionSuggestionService;
import com.finance.platform.ai.config.AiProperties;
import com.finance.platform.core.exception.ErrorCodes;
import com.finance.platform.finance.domain.model.Category;
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
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Optional;

@Slf4j
@Component
@RequiredArgsConstructor
public class OpenAiCompatibleExtractionProvider implements DocumentExtractionService, DocumentClassificationService, TransactionSuggestionService {

	private static final int MAX_VISION_BYTES = 4_000_000;

	private final AiProperties properties;
	private final ObjectMapper objectMapper;
	private final HttpClient httpClient = HttpClient.newBuilder()
			.connectTimeout(Duration.ofSeconds(15))
			.build();

	@Override
	public boolean isEnabled() {
		return properties.isExtractionEnabled() && properties.hasApiKey() && !properties.isMockExtraction();
	}

	@Override
	public Optional<ExtractedDocument> extract(byte[] content, String fileName, String mimeType) {
		return extract(content, fileName, mimeType, null);
	}

	public Optional<ExtractedDocument> extract(byte[] content, String fileName, String mimeType, String documentType) {
		if (!isEnabled()) {
			return Optional.empty();
		}
		try {
			ObjectNode body = chatBody(
					ExtractionPromptBuilder.systemPrompt(),
					ExtractionPromptBuilder.userPrompt(fileName, mimeType, documentType),
					content,
					mimeType
			);
			JsonNode root = complete(body);
			if (root == null) {
				return Optional.empty();
			}
			String contentJson = root.path("choices").path(0).path("message").path("content").asText();
			if (contentJson == null || contentJson.isBlank()) {
				return Optional.empty();
			}
			JsonNode extracted = objectMapper.readTree(contentJson);
			Integer inTokens = intOrNull(root.path("usage").path("prompt_tokens"));
			Integer outTokens = intOrNull(root.path("usage").path("completion_tokens"));
			return Optional.of(toExtracted(extracted, sanitizeRaw(contentJson), inTokens, outTokens, null));
		} catch (java.net.http.HttpTimeoutException ex) {
			log.warn("AI provider timeout documentFile={} provider=openai", fileName);
			return Optional.of(failed(ErrorCodes.AI_PROVIDER_TIMEOUT));
		} catch (Exception ex) {
			log.warn("AI extraction failed: {}", ex.getMessage());
			return Optional.of(failed(ErrorCodes.AI_PROVIDER_UNAVAILABLE));
		}
	}

	public Optional<JsonNode> suggestCategory(ExtractedDocument extracted, List<Category> categories) {
		if (!properties.isAccountingLlmEnabled()) {
			return Optional.empty();
		}
		try {
			ObjectNode body = chatBody(
					AccountingSuggestionPromptBuilder.systemPrompt(),
					AccountingSuggestionPromptBuilder.userPrompt(extracted, categories),
					null,
					null
			);
			JsonNode root = complete(body);
			if (root == null) {
				return Optional.empty();
			}
			String contentJson = root.path("choices").path(0).path("message").path("content").asText();
			if (contentJson == null || contentJson.isBlank()) {
				return Optional.empty();
			}
			return Optional.of(objectMapper.readTree(contentJson));
		} catch (Exception ex) {
			log.warn("AI suggestion call failed: {}", ex.getMessage());
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

	private JsonNode complete(ObjectNode body) throws Exception {
		HttpRequest request = HttpRequest.newBuilder()
				.uri(URI.create(trimSlash(properties.getOpenai().getBaseUrl()) + "/chat/completions"))
				.header("Authorization", "Bearer " + properties.getOpenai().getApiKey())
				.header("Content-Type", "application/json")
				.timeout(Duration.ofSeconds(Math.max(5, properties.getTimeoutSeconds())))
				.POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(body), StandardCharsets.UTF_8))
				.build();
		HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
		if (response.statusCode() == 429) {
			log.warn("AI provider rate limited HTTP 429");
			return null;
		}
		if (response.statusCode() >= 300) {
			log.warn("AI provider returned HTTP {}", response.statusCode());
			return null;
		}
		return objectMapper.readTree(response.body());
	}

	private ObjectNode chatBody(String system, String user, byte[] content, String mimeType) {
		ObjectNode body = objectMapper.createObjectNode();
		body.put("model", properties.getOpenai().getModel());
		body.set("response_format", objectMapper.createObjectNode().put("type", "json_object"));
		ArrayNode messages = body.putArray("messages");
		messages.addObject().put("role", "system").put("content", system);
		ObjectNode userMessage = messages.addObject();
		userMessage.put("role", "user");
		if (isVisionMime(mimeType) && content != null && content.length > 0 && content.length <= MAX_VISION_BYTES) {
			ArrayNode parts = userMessage.putArray("content");
			parts.addObject().put("type", "text").put("text", user);
			ObjectNode image = parts.addObject();
			image.put("type", "image_url");
			image.putObject("image_url").put("url", "data:" + mimeType + ";base64," + Base64.getEncoder().encodeToString(content));
		} else {
			userMessage.put("content", user);
		}
		return body;
	}

	private ExtractedDocument toExtracted(JsonNode node, String raw, Integer inTokens, Integer outTokens, String failureCode) {
		List<ExtractedDocument.LineItem> items = new ArrayList<>();
		JsonNode lines = node.get("lineItems");
		if (lines != null && lines.isArray()) {
			for (JsonNode line : lines) {
				items.add(new ExtractedDocument.LineItem(text(line, "description"), decimal(line, "amount"), decimal(line, "quantity")));
			}
		}
		return new ExtractedDocument(
				text(node, "documentType"),
				firstText(node, "supplierName", "merchantOrCustomer"),
				text(node, "customerName"),
				text(node, "invoiceNumber"),
				text(node, "receiptNumber"),
				date(node, "documentDate", "date"),
				date(node, "dueDate"),
				text(node, "currency"),
				decimal(node, "subtotal"),
				firstDecimal(node, "taxAmount", "tax"),
				decimal(node, "discountAmount"),
				firstDecimal(node, "totalAmount", "amount"),
				text(node, "paymentMethod"),
				text(node, "description"),
				items,
				decimal(node, "supplierConfidence"),
				decimal(node, "dateConfidence"),
				decimal(node, "amountConfidence"),
				decimal(node, "taxConfidence"),
				decimal(node, "overallConfidence"),
				text(node, "ocrText"),
				raw,
				text(node, "candidateCategoryCode"),
				text(node, "suggestedTransactionType"),
				inTokens,
				outTokens,
				failureCode
		);
	}

	private static ExtractedDocument failed(String code) {
		return new ExtractedDocument(
				null, null, null, null, null, null, null, null, null, null, null, null, null, null,
				List.of(), null, null, null, null, null, null, null, null, null, null, null, code
		);
	}

	private static boolean isVisionMime(String mimeType) {
		return mimeType != null && (mimeType.equalsIgnoreCase("image/jpeg")
				|| mimeType.equalsIgnoreCase("image/png")
				|| mimeType.equalsIgnoreCase("image/webp"));
	}

	private static String sanitizeRaw(String raw) {
		if (raw == null) {
			return null;
		}
		return raw.length() > 8000 ? raw.substring(0, 8000) : raw;
	}

	private static String firstText(JsonNode node, String... fields) {
		for (String field : fields) {
			String value = text(node, field);
			if (value != null) {
				return value;
			}
		}
		return null;
	}

	private static LocalDate date(JsonNode node, String... fields) {
		for (String field : fields) {
			String value = text(node, field);
			if (value == null) {
				continue;
			}
			try {
				return LocalDate.parse(value.substring(0, Math.min(10, value.length())));
			} catch (Exception ignored) {
				return null;
			}
		}
		return null;
	}

	private static BigDecimal firstDecimal(JsonNode node, String... fields) {
		for (String field : fields) {
			BigDecimal value = decimal(node, field);
			if (value != null) {
				return value;
			}
		}
		return null;
	}

	private static String text(JsonNode node, String field) {
		if (node == null) {
			return null;
		}
		JsonNode value = node.get(field);
		return value == null || value.isNull() || value.asText().isBlank() ? null : value.asText();
	}

	private static BigDecimal decimal(JsonNode node, String field) {
		if (node == null) {
			return null;
		}
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

	private static Integer intOrNull(JsonNode node) {
		return node == null || node.isMissingNode() || node.isNull() ? null : node.asInt();
	}

	private static String trimSlash(String url) {
		if (url == null) {
			return "";
		}
		return url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
	}
}
