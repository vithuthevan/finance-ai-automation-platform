package com.finance.platform.security;

import com.finance.platform.auth.application.dto.LoginRequest;
import com.finance.platform.support.AbstractPostgresIntegrationTest;
import com.finance.platform.support.IntegrationTestSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class JwtSecurityIntegrationTest extends AbstractPostgresIntegrationTest {

	@BeforeEach
	void setUp() {
		support.seedRolesIfNeeded();
	}

	@Test
	void missingTokenReturns401() throws Exception {
		mockMvc.perform(get("/api/v1/clients"))
				.andExpect(status().isUnauthorized());
	}

	@Test
	void malformedTokenReturns401() throws Exception {
		mockMvc.perform(get("/api/v1/clients")
						.header("Authorization", "Bearer not-a-jwt"))
				.andExpect(status().isUnauthorized());
	}

	@Test
	void wrongPasswordReturnsGenericFailure() throws Exception {
		var admin = support.registerFirmAdmin("Auth Firm");
		mockMvc.perform(post("/api/v1/auth/login")
						.contentType(MediaType.APPLICATION_JSON)
						.content(new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(
								new LoginRequest(admin.email(), "wrong-password"))))
				.andExpect(status().isBadRequest());
	}
}
