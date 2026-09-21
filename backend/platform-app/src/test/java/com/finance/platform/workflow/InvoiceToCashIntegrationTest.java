package com.finance.platform.workflow;

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

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class InvoiceToCashIntegrationTest extends AbstractPostgresIntegrationTest {

	private static final String IDEMPOTENCY = "Idempotency-Key";

	@Autowired
	private ObjectMapper objectMapper;

	private IntegrationTestSupport.Session admin;

	@BeforeEach
	void setUp() throws Exception {
		support.seedRolesIfNeeded();
		admin = support.registerFirmAdmin("Invoice Firm");
	}

	@Test
	void partialPayment_leavesOutstandingAndPartiallyPaid() throws Exception {
		UUID customerId = createCustomer();
		UUID invoiceId = createDraftInvoice(customerId);
		issueInvoice(invoiceId);
		JsonNode issued = getInvoice(invoiceId);
		UUID paymentId = recordPayment(customerId, BigDecimal.valueOf(40000));
		allocate(paymentId, invoiceId, BigDecimal.valueOf(40000));
		JsonNode after = getInvoice(invoiceId);
		assertThat(after.get("settlementStatus").asText()).isEqualTo("PARTIALLY_PAID");
		assertThat(after.get("outstanding").decimalValue()).isEqualByComparingTo(BigDecimal.valueOf(60000));
	}

	@Test
	void reverseAllocation_restoresOutstanding() throws Exception {
		UUID customerId = createCustomer();
		UUID invoiceId = createDraftInvoice(customerId);
		issueInvoice(invoiceId);
		JsonNode issued = getInvoice(invoiceId);
		UUID paymentId = recordPayment(customerId, issued.get("total").decimalValue());
		allocate(paymentId, invoiceId, issued.get("total").decimalValue());
		String paymentJson = mockMvc.perform(get("/api/v1/ar/payments/{id}", paymentId)
						.header(HttpHeaders.AUTHORIZATION, IntegrationTestSupport.bearer(admin.token())))
				.andExpect(status().isOk())
				.andReturn().getResponse().getContentAsString();
		UUID allocationId = UUID.fromString(
				objectMapper.readTree(paymentJson).get("allocations").get(0).get("id").asText());
		mockMvc.perform(post("/api/v1/ar/payments/{pid}/allocations/{aid}/reverse", paymentId, allocationId)
						.header(HttpHeaders.AUTHORIZATION, IntegrationTestSupport.bearer(admin.token()))
						.contentType(MediaType.APPLICATION_JSON)
						.content("{}"))
				.andExpect(status().isOk());
		JsonNode after = getInvoice(invoiceId);
		assertThat(after.get("settlementStatus").asText()).isEqualTo("UNPAID");
		assertThat(after.get("outstanding").decimalValue()).isEqualByComparingTo(issued.get("total").decimalValue());
	}

	@Test
	void invoiceIssuePaymentAllocate_setsOutstandingToZero() throws Exception {
		UUID customerId = createCustomer();
		UUID invoiceId = createDraftInvoice(customerId);
		issueInvoice(invoiceId);
		JsonNode issued = getInvoice(invoiceId);
		assertThat(issued.get("documentStatus").asText()).isEqualTo("ISSUED");
		assertThat(issued.get("invoiceNumber").asText()).startsWith("INV-");

		UUID paymentId = recordPayment(customerId, issued.get("total").decimalValue());
		allocate(paymentId, invoiceId, issued.get("total").decimalValue());

		JsonNode after = getInvoice(invoiceId);
		assertThat(after.get("settlementStatus").asText()).isEqualTo("PAID");
		assertThat(after.get("outstanding").decimalValue()).isEqualByComparingTo(BigDecimal.ZERO);
	}

	private UUID createCustomer() throws Exception {
		String body = """
				{"name":"Acme Retail","email":"billing@acme.test","paymentTermsDays":30}
				""";
		String json = mockMvc.perform(post("/api/v1/ar/customers")
						.header(HttpHeaders.AUTHORIZATION, IntegrationTestSupport.bearer(admin.token()))
						.contentType(MediaType.APPLICATION_JSON)
						.content(body))
				.andExpect(status().isCreated())
				.andReturn().getResponse().getContentAsString();
		return UUID.fromString(objectMapper.readTree(json).get("id").asText());
	}

	private UUID createDraftInvoice(UUID customerId) throws Exception {
		String body = """
				{
				  "customerId":"%s",
				  "lines":[{"description":"Monthly bookkeeping","quantity":1,"unitPrice":100000}]
				}
				""".formatted(customerId);
		String json = mockMvc.perform(post("/api/v1/ar/invoices")
						.header(HttpHeaders.AUTHORIZATION, IntegrationTestSupport.bearer(admin.token()))
						.contentType(MediaType.APPLICATION_JSON)
						.content(body))
				.andExpect(status().isCreated())
				.andReturn().getResponse().getContentAsString();
		return UUID.fromString(objectMapper.readTree(json).get("id").asText());
	}

	private void issueInvoice(UUID invoiceId) throws Exception {
		mockMvc.perform(post("/api/v1/ar/invoices/{id}/issue", invoiceId)
						.header(HttpHeaders.AUTHORIZATION, IntegrationTestSupport.bearer(admin.token()))
						.header(IDEMPOTENCY, UUID.randomUUID().toString()))
				.andExpect(status().isOk());
	}

	private JsonNode getInvoice(UUID invoiceId) throws Exception {
		String json = mockMvc.perform(get("/api/v1/ar/invoices/{id}", invoiceId)
						.header(HttpHeaders.AUTHORIZATION, IntegrationTestSupport.bearer(admin.token())))
				.andExpect(status().isOk())
				.andReturn().getResponse().getContentAsString();
		return objectMapper.readTree(json);
	}

	private UUID recordPayment(UUID customerId, BigDecimal amount) throws Exception {
		String body = """
				{"customerId":"%s","paymentDate":"2026-01-15","amount":%s,"reference":"TEST"}
				""".formatted(customerId, amount.toPlainString());
		String json = mockMvc.perform(post("/api/v1/ar/payments")
						.header(HttpHeaders.AUTHORIZATION, IntegrationTestSupport.bearer(admin.token()))
						.header(IDEMPOTENCY, UUID.randomUUID().toString())
						.contentType(MediaType.APPLICATION_JSON)
						.content(body))
				.andExpect(status().isCreated())
				.andReturn().getResponse().getContentAsString();
		return UUID.fromString(objectMapper.readTree(json).get("id").asText());
	}

	private void allocate(UUID paymentId, UUID invoiceId, BigDecimal amount) throws Exception {
		String body = """
				{"allocations":[{"invoiceId":"%s","amount":%s}]}
				""".formatted(invoiceId, amount.toPlainString());
		mockMvc.perform(post("/api/v1/ar/payments/{id}/allocate", paymentId)
						.header(HttpHeaders.AUTHORIZATION, IntegrationTestSupport.bearer(admin.token()))
						.header(IDEMPOTENCY, UUID.randomUUID().toString())
						.contentType(MediaType.APPLICATION_JSON)
						.content(body))
				.andExpect(status().isOk());
	}
}
