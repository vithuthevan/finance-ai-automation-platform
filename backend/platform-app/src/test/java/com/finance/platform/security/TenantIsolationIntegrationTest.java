package com.finance.platform.security;

import com.finance.platform.finance.application.dto.ClientResponse;
import com.finance.platform.support.AbstractPostgresIntegrationTest;
import com.finance.platform.support.IntegrationTestSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class TenantIsolationIntegrationTest extends AbstractPostgresIntegrationTest {

	@BeforeEach
	void setUp() {
		support.seedRolesIfNeeded();
	}

	@Test
	void firmACannotAccessFirmBClientById() throws Exception {
		var firmA = support.registerFirmAdmin("Tenant A");
		var firmB = support.registerFirmAdmin("Tenant B");

		ClientResponse clientB = createClient(firmB.token(), "Client B");

		mockMvc.perform(get("/api/v1/clients/" + clientB.id())
						.header(HttpHeaders.AUTHORIZATION, IntegrationTestSupport.bearer(firmA.token())))
				.andExpect(status().isNotFound());
	}

	@Test
	void firmACannotListFirmBSubscriptionUsage() throws Exception {
		var firmA = support.registerFirmAdmin("Usage A");
		support.registerFirmAdmin("Usage B");

		mockMvc.perform(get("/api/v1/subscription/usage")
						.header(HttpHeaders.AUTHORIZATION, IntegrationTestSupport.bearer(firmA.token())))
				.andExpect(status().isOk());
	}

	@Test
	void firmAdminCannotAccessPlatformMetrics() throws Exception {
		var firmA = support.registerFirmAdmin("Platform Block A");

		mockMvc.perform(get("/api/v1/platform/metrics")
						.header(HttpHeaders.AUTHORIZATION, IntegrationTestSupport.bearer(firmA.token())))
				.andExpect(status().isForbidden());
	}

	private ClientResponse createClient(String token, String name) throws Exception {
		var result = mockMvc.perform(post("/api/v1/clients")
						.header(HttpHeaders.AUTHORIZATION, IntegrationTestSupport.bearer(token))
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"name\":\"" + name + "\"}"))
				.andExpect(status().isCreated())
				.andReturn();
		return support.read(result, ClientResponse.class);
	}
}
