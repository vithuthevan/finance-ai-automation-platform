package com.finance.platform.ai.application;

import com.fasterxml.jackson.databind.JsonNode;
import com.finance.platform.ai.provider.OpenAiCompatibleExtractionProvider;
import com.finance.platform.finance.domain.model.Category;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class DefaultAccountingSuggestionService implements AccountingSuggestionService {

	private final ObjectProvider<OpenAiCompatibleExtractionProvider> openAiProvider;

	@Override
	public AccountingSuggestion suggest(ExtractedDocument extracted, List<Category> validCategories, Category historical) {
		String type = normalizeType(extracted == null ? null : extracted.suggestedTransactionType(), extracted);
		if (historical != null && categoryAllowed(historical, validCategories, type)) {
			return toSuggestion(extracted, historical, type, extracted != null ? extracted.overallConfidence() : null,
					"Historical vendor/customer match", "HISTORICAL");
		}
		Category byCode = resolveCategory(extracted == null ? null : extracted.candidateCategoryCode(), validCategories, type);
		if (byCode != null) {
			return toSuggestion(extracted, byCode, type, extracted != null ? extracted.overallConfidence() : null,
					"Resolved extracted category code against the client chart", "EXTRACTED_CODE");
		}
		OpenAiCompatibleExtractionProvider llmProvider = openAiProvider.getIfAvailable();
		Optional<JsonNode> llm = llmProvider == null
				? Optional.empty()
				: llmProvider.suggestCategory(extracted, validCategories);
		if (llm.isPresent()) {
			JsonNode node = llm.get();
			String llmType = normalizeType(text(node, "transactionType"), extracted);
			Category resolved = resolveCategory(text(node, "categoryCode"), validCategories, llmType);
			String description = text(node, "description");
			BigDecimal confidence = decimal(node, "confidence");
			return new AccountingSuggestion(
					llmType,
					resolved != null ? resolved.getId() : null,
					resolved != null ? resolved.getCode() : null,
					resolved != null ? resolved.getName() : null,
					description != null ? description : (extracted != null ? extracted.description() : null),
					extracted != null ? extracted.totalAmount() : null,
					extracted != null ? extracted.taxAmount() : null,
					extracted != null ? extracted.documentDate() : null,
					extracted != null ? extracted.paymentMethod() : null,
					extracted != null ? extracted.currency() : null,
					confidence,
					text(node, "reasoningSummary"),
					"LLM"
			);
		}
		return toSuggestion(extracted, null, type, extracted != null ? extracted.overallConfidence() : null,
				"No validated category. Accountant must select one.", "NONE");
	}

	private static AccountingSuggestion toSuggestion(
			ExtractedDocument extracted,
			Category category,
			String type,
			BigDecimal confidence,
			String reasoning,
			String source
	) {
		return new AccountingSuggestion(
				type,
				category != null ? category.getId() : null,
				category != null ? category.getCode() : null,
				category != null ? category.getName() : null,
				extracted != null ? extracted.description() : null,
				extracted != null ? extracted.totalAmount() : null,
				extracted != null ? extracted.taxAmount() : null,
				extracted != null ? extracted.documentDate() : null,
				extracted != null ? extracted.paymentMethod() : null,
				extracted != null ? extracted.currency() : null,
				confidence,
				reasoning,
				source
		);
	}

	private static Category resolveCategory(String codeOrName, List<Category> categories, String type) {
		if (codeOrName == null || codeOrName.isBlank()) {
			return null;
		}
		if (looksLikeUuid(codeOrName)) {
			return null;
		}
		String needle = codeOrName.trim();
		return categories.stream()
				.filter(Category::isActive)
				.filter(category -> categoryAllowed(category, categories, type))
				.filter(category -> needle.equalsIgnoreCase(category.getCode()) || needle.equalsIgnoreCase(category.getName()))
				.findFirst()
				.orElse(null);
	}

	private static boolean categoryAllowed(Category category, List<Category> valid, String type) {
		if (category == null || !category.isActive() || valid.stream().noneMatch(item -> item.getId().equals(category.getId()))) {
			return false;
		}
		if ("INCOME".equals(type)) {
			return category.getCategoryType() != Category.CategoryType.EXPENSE;
		}
		if ("EXPENSE".equals(type)) {
			return category.getCategoryType() != Category.CategoryType.INCOME;
		}
		return true;
	}

	private static String normalizeType(String raw, ExtractedDocument extracted) {
		if (raw != null) {
			String value = raw.trim().toUpperCase(Locale.ROOT);
			if ("EXPENSE".equals(value) || "INCOME".equals(value) || "UNKNOWN".equals(value)) {
				return value;
			}
		}
		if (extracted != null && extracted.documentType() != null) {
			String documentType = extracted.documentType().toUpperCase(Locale.ROOT);
			if (documentType.contains("SALES")) {
				return "INCOME";
			}
			if (documentType.contains("RECEIPT") || documentType.contains("PURCHASE")) {
				return "EXPENSE";
			}
		}
		return "UNKNOWN";
	}

	private static boolean looksLikeUuid(String value) {
		try {
			UUID.fromString(value.trim());
			return true;
		} catch (Exception ex) {
			return false;
		}
	}

	private static String text(JsonNode node, String field) {
		JsonNode value = node.get(field);
		return value == null || value.isNull() || value.asText().isBlank() ? null : value.asText();
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
}
