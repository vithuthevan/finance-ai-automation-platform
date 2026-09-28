package com.finance.platform.security;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.finance.platform.auth.application.dto.LoginRequest;
import com.finance.platform.auth.application.dto.RegisterRequest;
import com.finance.platform.auth.infrastructure.security.LoginLockoutService;
import com.finance.platform.auth.infrastructure.security.ratelimit.InMemoryRateLimitStore;
import com.finance.platform.support.AbstractPostgresIntegrationTest;
import com.finance.platform.support.IntegrationTestSupport;
import com.finance.platform.support.TestTags;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Tag(TestTags.AUTH_SECURITY)
@TestPropertySource(properties = {
		"app.auth.rate-limit-enabled=true",
		"app.auth.login.max-attempts=3",
		"app.auth.login.window-seconds=3600",
		"app.auth.register.max-attempts=2",
		"app.auth.register.window-seconds=3600",
		"app.auth.forgot-password.max-attempts=1",
		"app.auth.forgot-password.window-seconds=3600",
		"app.auth.refresh.max-attempts=3",
		"app.auth.refresh.window-seconds=3600"
})
class AuthAbuseProtectionIntegrationTest extends AbstractPostgresIntegrationTest {

	@Autowired
	private ObjectMapper objectMapper;

	@Autowired
	private InMemoryRateLimitStore rateLimitStore;

	@Autowired
	private LoginLockoutService loginLockoutService;

	@DynamicPropertySource
	static void enableAuthRateLimits(DynamicPropertyRegistry registry) {
		registry.add("app.auth.rate-limit-enabled", () -> true);
	}

	@BeforeEach
	void seedRoles() throws Exception {
		rateLimitStore.resetAll();
		loginLockoutService.resetAll();
		support.seedRolesIfNeeded();
	}

	@Test
	void loginRateLimitReturns429WithRetryAfter() throws Exception {
		String email = "rate-login-" + IntegrationTestSupport.uniqueSuffix() + "@example.com";
		registerUser(email);

		for (int i = 0; i < 3; i++) {
			mockMvc.perform(post("/api/v1/auth/login")
							.contentType(MediaType.APPLICATION_JSON)
							.content(objectMapper.writeValueAsString(new LoginRequest(email, "wrong"))))
					.andExpect(status().isUnprocessableEntity());
		}

		var result = mockMvc.perform(post("/api/v1/auth/login")
						.contentType(MediaType.APPLICATION_JSON)
						.content(objectMapper.writeValueAsString(new LoginRequest(email, "wrong"))))
				.andExpect(status().isTooManyRequests())
				.andExpect(header().exists("Retry-After"))
				.andReturn();

		JsonNode body = objectMapper.readTree(result.getResponse().getContentAsString());
		assertThat(body.get("errorCode").asText()).isEqualTo("AUTH_RATE_LIMITED");
	}

	@Test
	void forwardedHeaderCannotBypassIpRateLimit() throws Exception {
		String email = "xff-" + IntegrationTestSupport.uniqueSuffix() + "@example.com";
		registerUser(email);

		for (int i = 0; i < 3; i++) {
			mockMvc.perform(post("/api/v1/auth/login")
							.header("X-Forwarded-For", "10.0.0." + i)
							.contentType(MediaType.APPLICATION_JSON)
							.content(objectMapper.writeValueAsString(new LoginRequest(email, "wrong"))))
					.andExpect(status().isUnprocessableEntity());
		}

		mockMvc.perform(post("/api/v1/auth/login")
						.header("X-Forwarded-For", "10.0.0.99")
						.contentType(MediaType.APPLICATION_JSON)
						.content(objectMapper.writeValueAsString(new LoginRequest(email, "wrong"))))
				.andExpect(status().isTooManyRequests());
	}

	@Test
	void registrationThrottled() throws Exception {
		String suffix = IntegrationTestSupport.uniqueSuffix();
		for (int i = 0; i < 2; i++) {
			mockMvc.perform(post("/api/v1/auth/register")
							.contentType(MediaType.APPLICATION_JSON)
							.content(objectMapper.writeValueAsString(new RegisterRequest(
									"Firm " + suffix + i,
									null,
									"reg-" + suffix + "-" + i + "@example.com",
									IntegrationTestSupport.PASSWORD,
									"Admin"
							))))
					.andExpect(status().isCreated());
		}

		mockMvc.perform(post("/api/v1/auth/register")
						.contentType(MediaType.APPLICATION_JSON)
						.content(objectMapper.writeValueAsString(new RegisterRequest(
								"Firm overflow " + suffix,
								null,
								"reg-overflow-" + suffix + "@example.com",
								IntegrationTestSupport.PASSWORD,
								"Admin"
						))))
				.andExpect(status().isTooManyRequests());
	}

	@Test
	void forgotPasswordThrottledWithoutEnumeration() throws Exception {
		String suffix = IntegrationTestSupport.uniqueSuffix();
		mockMvc.perform(post("/api/v1/auth/forgot-password")
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"email\":\"missing-" + suffix + "@example.com\"}"))
				.andExpect(status().isOk());

		mockMvc.perform(post("/api/v1/auth/forgot-password")
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"email\":\"missing2-" + suffix + "@example.com\"}"))
				.andExpect(status().isTooManyRequests());
	}

	@Test
	void refreshAbuseThrottled() throws Exception {
		for (int i = 0; i < 3; i++) {
			mockMvc.perform(post("/api/v1/auth/refresh")
							.contentType(MediaType.APPLICATION_JSON)
							.content("{\"refreshToken\":\"invalid-" + i + "\"}"))
					.andExpect(status().isUnprocessableEntity());
		}

		mockMvc.perform(post("/api/v1/auth/refresh")
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"refreshToken\":\"invalid-final\"}"))
				.andExpect(status().isTooManyRequests());
	}

	private void registerUser(String email) throws Exception {
		String suffix = IntegrationTestSupport.uniqueSuffix();
		mockMvc.perform(post("/api/v1/auth/register")
						.contentType(MediaType.APPLICATION_JSON)
						.content(objectMapper.writeValueAsString(new RegisterRequest(
								"Rate Firm " + suffix,
								null,
								email,
								IntegrationTestSupport.PASSWORD,
								"Admin " + suffix
						))))
				.andExpect(status().isCreated());
	}
}
