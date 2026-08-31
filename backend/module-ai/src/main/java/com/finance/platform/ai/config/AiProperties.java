package com.finance.platform.ai.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Getter
@Setter
@ConfigurationProperties(prefix = "app.ai")
public class AiProperties {

	private boolean enabled = false;
	private String provider = "none";
	private String extractionProvider;
	private String accountingProvider;
	private int timeoutSeconds = 45;
	private int maxRetriesPerDocument = 5;
	private final OpenAi openai = new OpenAi();

	@Getter
	@Setter
	public static class OpenAi {
		private String apiKey;
		private String baseUrl = "https://api.openai.com/v1";
		private String model = "gpt-4o-mini";
	}

	public String effectiveExtractionProvider() {
		return firstNonBlank(extractionProvider, provider, "none");
	}

	public String effectiveAccountingProvider() {
		return firstNonBlank(accountingProvider, provider, "none");
	}

	public boolean isExtractionEnabled() {
		if (!enabled) {
			return false;
		}
		String name = effectiveExtractionProvider();
		if (isDisabledName(name)) {
			return false;
		}
		if (isMockName(name)) {
			return true;
		}
		return isOpenAiName(name) && hasApiKey();
	}

	public boolean isAccountingLlmEnabled() {
		if (!enabled) {
			return false;
		}
		String name = effectiveAccountingProvider();
		return isOpenAiName(name) && hasApiKey();
	}

	public boolean isMockExtraction() {
		return enabled && isMockName(effectiveExtractionProvider());
	}

	public boolean hasApiKey() {
		return openai.getApiKey() != null && !openai.getApiKey().isBlank();
	}

	public boolean isProviderConfigured() {
		return isExtractionEnabled();
	}

	private static boolean isDisabledName(String name) {
		return name == null || name.isBlank()
				|| "none".equalsIgnoreCase(name)
				|| "disabled".equalsIgnoreCase(name)
				|| "manual".equalsIgnoreCase(name);
	}

	private static boolean isOpenAiName(String name) {
		return "openai".equalsIgnoreCase(name)
				|| "openai-compatible".equalsIgnoreCase(name)
				|| "llm".equalsIgnoreCase(name);
	}

	private static boolean isMockName(String name) {
		return "mock".equalsIgnoreCase(name);
	}

	private static String firstNonBlank(String... values) {
		for (String value : values) {
			if (value != null && !value.isBlank()) {
				return value;
			}
		}
		return "none";
	}
}
