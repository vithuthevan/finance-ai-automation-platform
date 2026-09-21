package com.finance.platform.security;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.finance.platform.support.AbstractPostgresIntegrationTest;
import com.finance.platform.support.IntegrationTestSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;

import java.math.BigDecimal;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class ArTenantIsolationIntegrationTest extends AbstractPostgresIntegrationTest {

	@Autowired
	private ObjectMapper objectMapper;

	private IntegrationTestSupport.Session firmA;
	private IntegrationTestSupport.Session firmB;
	private UUID customerB;
	private UUID invoiceB;
	private UUID paymentB;

	@BeforeEach
	void setUp() throws Exception {
		support.seedRolesIfNeeded();
		firmA = support.registerFirmAdmin("AR Iso A");
		firmB = support.registerFirmAdmin("AR Iso B");
		customerB = createCustomer(firmB);
		invoiceB = createAndIssueInvoice(firmB, customerB);
		paymentB = recordPayment(firmB, customerB, BigDecimal.valueOf(1000));
	}

	@Test
	void firmACannotAccessFirmBCustomer() throws Exception {
		mockMvc.perform(get("/api/v1/ar/customers/{id}", customerB)
						.header(HttpHeaders.AUTHORIZATION, IntegrationTestSupport.bearer(firmA.token())))
				.andExpect(status().isNotFound());
	}

	@Test
	void firmACannotAccessFirmBInvoice() throws Exception {
		mockMvc.perform(get("/api/v1/ar/invoices/{id}", invoiceB)
						.header(HttpHeaders.AUTHORIZATION, IntegrationTestSupport.bearer(firmA.token())))
				.andExpect(status().isNotFound());
	}

	@Test
	void firmACannotAccessFirmBPayment() throws Exception {
		mockMvc.perform(get("/api/v1/ar/payments/{id}", paymentB)
						.header(HttpHeaders.AUTHORIZATION, IntegrationTestSupport.bearer(firmA.token())))
				.andExpect(status().isNotFound());
	}

	@Test
	void firmACannotAllocateAgainstFirmBInvoice() throws Exception {
		UUID paymentA = recordPayment(firmA, createCustomer(firmA), BigDecimal.TEN);
		String body = """
				{"allocations":[{"invoiceId":"%s","amount":10}]}
				""".formatted(invoiceB);
		mockMvc.perform(post("/api/v1/ar/payments/{id}/allocate", paymentA)
						.header(HttpHeaders.AUTHORIZATION, IntegrationTestSupport.bearer(firmA.token()))
						.contentType(MediaType.APPLICATION_JSON)
						.content(body))
				.andExpect(status().is4xxClientError());
	}

	private UUID createCustomer(IntegrationTestSupport.Session session) throws Exception {
		String body = """
				{"name":"Customer","email":"c@test.com","paymentTermsDays":30}
				""";
		String json = mockMvc.perform(post("/api/v1/ar/customers")
						.header(HttpHeaders.AUTHORIZATION, IntegrationTestSupport.bearer(session.token()))
						.contentType(MediaType.APPLICATION_JSON)
						.content(body))
				.andExpect(status().isCreated())
				.andReturn().getResponse().getContentAsString();
		return UUID.fromString(objectMapper.readTree(json).get("id").asText());
	}

	private UUID createAndIssueInvoice(IntegrationTestSupport.Session session, UUID customerId) throws Exception {
		String body = """
				{"customerId":"%s","lines":[{"description":"Svc","quantity":1,"unitPrice":1000}]}
				""".formatted(customerId);
		String json = mockMvc.perform(post("/api/v1/ar/invoices")
						.header(HttpHeaders.AUTHORIZATION, IntegrationTestSupport.bearer(session.token()))
						.contentType(MediaType.APPLICATION_JSON)
						.content(body))
				.andExpect(status().isCreated())
				.andReturn().getResponse().getContentAsString();
		UUID invoiceId = UUID.fromString(objectMapper.readTree(json).get("id").asText());
		mockMvc.perform(post("/api/v1/ar/invoices/{id}/issue", invoiceId)
						.header(HttpHeaders.AUTHORIZATION, IntegrationTestSupport.bearer(session.token()))
						.header("Idempotency-Key", UUID.randomUUID().toString()))
				.andExpect(status().isOk());
		return invoiceId;
	}

	private UUID recordPayment(IntegrationTestSupport.Session session, UUID customerId, BigDecimal amount) throws Exception {
		String body = """
				{"customerId":"%s","paymentDate":"2026-01-15","amount":%s,"reference":"T"}
				""".formatted(customerId, amount.toPlainString());
		String json = mockMvc.perform(post("/api/v1/ar/payments")
						.header(HttpHeaders.AUTHORIZATION, IntegrationTestSupport.bearer(session.token()))
						.contentType(MediaType.APPLICATION_JSON)
						.content(body))
				.andExpect(status().isCreated())
				.andReturn().getResponse().getContentAsString();
		return UUID.fromString(objectMapper.readTree(json).get("id").asText());
	}
}
