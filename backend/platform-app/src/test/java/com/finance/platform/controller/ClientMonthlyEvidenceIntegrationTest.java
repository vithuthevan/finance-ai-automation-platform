package com.finance.platform.controller;

import com.finance.platform.finance.application.dto.ClientResponse;
import com.finance.platform.support.AbstractPostgresIntegrationTest;
import com.finance.platform.support.IntegrationTestSupport.Session;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class ClientMonthlyEvidenceIntegrationTest extends AbstractPostgresIntegrationTest {

	private Session admin;
	private UUID clientId;

	@BeforeEach
	void setUp() throws Exception {
		support.seedRolesIfNeeded();
		admin = support.registerFirmAdmin("Evidence Checklist Firm");
		MvcResult clientResult = mockMvc.perform(post("/api/v1/clients")
						.header("Authorization", "Bearer " + admin.token())
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"name\":\"Checklist Client\",\"contactEmail\":\"c@example.com\"}"))
				.andExpect(status().isCreated())
				.andReturn();
		clientId = support.read(clientResult, ClientResponse.class).id();
	}

	@Test
	void createListAndGenerateRequests() throws Exception {
		mockMvc.perform(post("/api/v1/clients/" + clientId + "/monthly-evidence")
						.header("Authorization", "Bearer " + admin.token())
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"title":"Bank statement","description":"Full month","documentType":"BANK_STATEMENT","required":true}
								"""))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.title").value("Bank statement"));

		mockMvc.perform(get("/api/v1/clients/" + clientId + "/monthly-evidence")
						.header("Authorization", "Bearer " + admin.token()))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$[0].title").value("Bank statement"));

		mockMvc.perform(post("/api/v1/clients/" + clientId + "/monthly-evidence/generate-requests")
						.header("Authorization", "Bearer " + admin.token())
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"year\":2026,\"month\":9}"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.created").value(1));
	}
}
