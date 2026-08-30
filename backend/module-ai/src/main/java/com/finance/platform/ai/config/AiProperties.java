package com.finance.platform.ai.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Getter
@Setter
@ConfigurationProperties(prefix = "app.ai")
public class AiProperties {

	private boolean enabled = false;
	private String provider = "disabled";
	private final OpenAi openai = new OpenAi();

	@Getter
	@Setter
	public static class OpenAi {
		private String apiKey;
		private String baseUrl = "https://api.openai.com/v1";
		private String model = "gpt-4o-mini";
	}

	public boolean isProviderConfigured() {
		if (!enabled) {
			return false;
		}
		if (!"openai".equalsIgnoreCase(provider) && !"openai-compatible".equalsIgnoreCase(provider)) {
			return false;
		}
		return openai.getApiKey() != null && !openai.getApiKey().isBlank();
	}
}
