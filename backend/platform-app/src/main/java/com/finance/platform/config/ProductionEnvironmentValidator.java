package com.finance.platform.config;

import com.finance.platform.core.storage.StorageProperties;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.annotation.Profile;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Component
@Profile("prod")
public class ProductionEnvironmentValidator {

	@Value("${app.email.provider}")
	private String emailProvider;

	@Value("${app.email.host:}")
	private String mailHost;

	@Value("${app.email.username:}")
	private String mailUsername;

	@Value("${app.email.password:}")
	private String mailPassword;

	@Value("${app.email.from:}")
	private String mailFrom;

	@Value("${app.frontend.base-url:}")
	private String frontendBaseUrl;

	private final StorageProperties storageProperties;

	public ProductionEnvironmentValidator(StorageProperties storageProperties) {
		this.storageProperties = storageProperties;
	}

	@EventListener(ApplicationReadyEvent.class)
	public void validate() {
		requireNonBlank(frontendBaseUrl, "APP_FRONTEND_BASE_URL");

		if ("log".equalsIgnoreCase(emailProvider)) {
			throw new IllegalStateException(
					"APP_EMAIL_PROVIDER=log is not allowed in production; configure SMTP or disable email-dependent workflows explicitly");
		}

		if ("smtp".equalsIgnoreCase(emailProvider)) {
			requireNonBlank(mailHost, "MAIL_HOST");
			requireNonBlank(mailUsername, "MAIL_USERNAME");
			requireNonBlank(mailPassword, "MAIL_PASSWORD");
			requireNonBlank(mailFrom, "MAIL_FROM");
		}

		if ("s3".equalsIgnoreCase(storageProperties.getProvider())) {
			requireNonBlank(storageProperties.getS3().getBucket(), "APP_STORAGE_S3_BUCKET");
			requireNonBlank(storageProperties.getS3().getAccessKey(), "APP_STORAGE_S3_ACCESS_KEY");
			requireNonBlank(storageProperties.getS3().getSecretKey(), "APP_STORAGE_S3_SECRET_KEY");
			requireNonBlank(storageProperties.getS3().getRegion(), "APP_STORAGE_S3_REGION");
		}
	}

	private static void requireNonBlank(String value, String name) {
		if (value == null || value.isBlank()) {
			throw new IllegalStateException(name + " must be configured in production");
		}
	}
}
