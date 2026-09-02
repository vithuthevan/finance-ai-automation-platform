package com.finance.platform.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

	@Bean
	OpenAPI financePlatformOpenApi() {
		return new OpenAPI()
				.info(new Info()
						.title("Finance Platform API")
						.version("v1")
						.description("Auth, users, clients, categories, expenses, income, documents, reports, month-end close, and audit. Close: periods, readiness, review, close, reopen, document requests, work-queue. Accept creates DRAFT only. Report dates use ISO-8601 yyyy-MM-dd. Exports: text/csv and XLSX. UPLOAD_ONLY is denied reporting, AI review, and close management."))
				.addSecurityItem(new SecurityRequirement().addList("bearer-jwt"))
				.components(new Components().addSecuritySchemes("bearer-jwt", new SecurityScheme()
						.type(SecurityScheme.Type.HTTP)
						.scheme("bearer")
						.bearerFormat("JWT")));
	}
}
