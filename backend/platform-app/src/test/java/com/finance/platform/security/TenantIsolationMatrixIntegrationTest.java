package com.finance.platform.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.finance.platform.core.notification.NotificationJpaRepository;
import com.finance.platform.finance.application.dto.CreateExpenseRequest;
import com.finance.platform.finance.application.dto.ExpenseResponse;
import com.finance.platform.finance.application.dto.LinkDocumentRequest;
import com.finance.platform.finance.application.dto.UpdateCategoryRequest;
import com.finance.platform.finance.application.dto.UpdateExpenseRequest;
import com.finance.platform.finance.domain.model.Category;
import com.finance.platform.support.AbstractPostgresIntegrationTest;
import com.finance.platform.support.IntegrationTestSupport;
import com.finance.platform.support.TestTags;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Systematic cross-tenant (firm) isolation matrix: tenant A must not read or mutate tenant B resources.
 * Enforcement is primarily {@link com.finance.platform.finance.application.service.ClientAccessService}
 * ({@code findByIdAndFirmId}) plus firm-scoped repositories on firm-global resources (categories, AR).
 */
@Tag(TestTags.TENANT_SECURITY)
class TenantIsolationMatrixIntegrationTest extends AbstractPostgresIntegrationTest {

	private static final String IDEMPOTENCY = "Idempotency-Key";

	@Autowired
	private ObjectMapper objectMapper;

	@Autowired
	private NotificationJpaRepository notificationRepository;

	private IntegrationTestSupport.Session attacker;
	private TenantIsolationVictimResources victim;
	private UUID victimNotificationId;

	@BeforeEach
	void setUp() throws Exception {
		support.seedRolesIfNeeded();
		attacker = support.registerFirmAdmin("Attacker Firm");
		victim = TenantIsolationDataSeeder.seedVictimFirm(mockMvc, support, objectMapper, "Victim Firm");
		victimNotificationId = notificationRepository.save(TenantIsolationDataSeeder.victimNotification(victim)).getId();
	}

	@TestFactory
	Collection<DynamicTest> crossTenantReads_matrix() {
		LocalDate from = LocalDate.now().withDayOfMonth(1);
		LocalDate to = LocalDate.now();
		UUID clientId = victim.clientId();
		List<CrossTenantReadCase> reads = List.of(
				new CrossTenantReadCase("GET client", get("/api/v1/clients/" + clientId)),
				new CrossTenantReadCase("GET expense", get("/api/v1/clients/" + clientId + "/expenses/" + victim.expenseId())),
				new CrossTenantReadCase("GET income", get("/api/v1/clients/" + clientId + "/income/" + victim.incomeId())),
				new CrossTenantReadCase("GET category", get("/api/v1/categories/" + victim.expenseCategoryId())),
				new CrossTenantReadCase("GET document", get("/api/v1/clients/" + clientId + "/documents/" + victim.documentId())),
				new CrossTenantReadCase("GET document content", get("/api/v1/clients/" + clientId + "/documents/" + victim.documentId() + "/content")),
				new CrossTenantReadCase("GET bank accounts", get("/api/v1/clients/" + clientId + "/bank/accounts")),
				new CrossTenantReadCase("GET bank transactions", get("/api/v1/clients/" + clientId + "/bank/transactions")
						.param("bankAccountId", victim.bankAccountId().toString())),
				new CrossTenantReadCase("GET bank imports", get("/api/v1/clients/" + clientId + "/bank/imports")),
				new CrossTenantReadCase("GET period", get("/api/v1/clients/" + clientId + "/periods/" + victim.periodId())),
				new CrossTenantReadCase("GET period readiness", get("/api/v1/clients/" + clientId + "/periods/" + victim.periodId() + "/readiness")),
				new CrossTenantReadCase("GET user", get("/api/v1/users/" + victim.victim().userId())),
				new CrossTenantReadCase("GET AR customer", get("/api/v1/ar/customers/" + victim.arCustomerId())),
				new CrossTenantReadCase("GET AR invoice", get("/api/v1/ar/invoices/" + victim.arInvoiceId())),
				new CrossTenantReadCase("GET AR payment", get("/api/v1/ar/payments/" + victim.arPaymentId())),
				new CrossTenantReadCase("GET sales invoice", get("/api/v1/ar/invoices/" + victim.salesInvoiceId())),
				new CrossTenantReadCase("GET invoice PDF", get("/api/v1/ar/invoices/" + victim.salesInvoiceId() + "/pdf")),
				new CrossTenantReadCase("GET P&L report", get("/api/v1/clients/" + clientId + "/reports/profit-and-loss")
						.param("from", from.toString())
						.param("to", to.toString())),
				new CrossTenantReadCase("GET P&L export", get("/api/v1/clients/" + clientId + "/reports/profit-and-loss/export")
						.param("from", from.toString())
						.param("to", to.toString())
						.param("format", "csv"))
		);
		return reads.stream()
				.map(case_ -> DynamicTest.dynamicTest(case_.label(), () -> assertNotFound(case_.request())))
				.toList();
	}

	@Test
	void crossTenantWrites_blocked() throws Exception {
		UUID clientId = victim.clientId();
		UpdateExpenseRequest expenseUpdate = new UpdateExpenseRequest(
				LocalDate.now(),
				victim.expenseCategoryId(),
				new BigDecimal("999.00"),
				"LKR",
				"Hacked Vendor",
				null,
				null,
				null
		);
		assertNotFound(put("/api/v1/clients/" + clientId + "/expenses/" + victim.expenseId())
				.contentType(MediaType.APPLICATION_JSON)
				.content(objectMapper.writeValueAsString(expenseUpdate)));
		assertNotFound(delete("/api/v1/clients/" + clientId + "/expenses/" + victim.expenseId()));
		assertNotFound(post("/api/v1/clients/" + clientId + "/expenses/" + victim.expenseId() + "/approve")
				.header(IDEMPOTENCY, UUID.randomUUID().toString()));
		assertNotFound(post("/api/v1/clients/" + clientId + "/periods/" + victim.periodId() + "/close")
				.header(IDEMPOTENCY, UUID.randomUUID().toString())
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"closeNote\":\"illegal close\"}"));
		UpdateCategoryRequest categoryUpdate = new UpdateCategoryRequest(
				"HACK", "Hacked", Category.CategoryType.EXPENSE, null);
		assertNotFound(put("/api/v1/categories/" + victim.expenseCategoryId())
				.contentType(MediaType.APPLICATION_JSON)
				.content(objectMapper.writeValueAsString(categoryUpdate)));
		assertNotFound(post("/api/v1/users/" + victim.victim().userId() + "/deactivate"));
	}

	@Test
	void crossTenantNotificationMarkRead_blocked() throws Exception {
		mockMvc.perform(post("/api/v1/notifications/" + victimNotificationId + "/read")
						.header(HttpHeaders.AUTHORIZATION, IntegrationTestSupport.bearer(attacker.token())))
				.andExpect(status().isNotFound());
	}

	@Test
	void indirectIdor_reconcileVictimBankWithAttackerExpense_blocked() throws Exception {
		ExpenseResponse attackerExpense = createAttackerExpense();
		mockMvc.perform(post("/api/v1/clients/" + victim.clientId() + "/bank/transactions/"
						+ victim.bankTransactionId() + "/confirm")
						.header(HttpHeaders.AUTHORIZATION, IntegrationTestSupport.bearer(victim.victim().token()))
						.header(IDEMPOTENCY, UUID.randomUUID().toString())
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"expenseId\":\"" + attackerExpense.id() + "\"}"))
				.andExpect(status().is4xxClientError());
	}

	@Test
	void indirectIdor_attackerReconcileWithVictimExpense_blocked() throws Exception {
		mockMvc.perform(post("/api/v1/clients/" + victim.clientId() + "/bank/transactions/"
						+ victim.bankTransactionId() + "/confirm")
						.header(HttpHeaders.AUTHORIZATION, IntegrationTestSupport.bearer(attacker.token()))
						.header(IDEMPOTENCY, UUID.randomUUID().toString())
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"expenseId\":\"" + victim.expenseId() + "\"}"))
				.andExpect(status().isNotFound());
	}

	@Test
	void indirectIdor_linkVictimDocumentToAttackerExpense_blocked() throws Exception {
		ExpenseResponse attackerExpense = createAttackerExpense();
		mockMvc.perform(post("/api/v1/clients/" + attackerExpense.clientId() + "/expenses/" + attackerExpense.id() + "/documents")
						.header(HttpHeaders.AUTHORIZATION, IntegrationTestSupport.bearer(attacker.token()))
						.contentType(MediaType.APPLICATION_JSON)
						.content(objectMapper.writeValueAsString(new LinkDocumentRequest(victim.documentId()))))
				.andExpect(status().is4xxClientError());
	}

	@Test
	void indirectIdor_arAllocateAttackerPaymentToVictimInvoice_blocked() throws Exception {
		UUID attackerCustomer = createAttackerArCustomer();
		UUID attackerPayment = recordAttackerPayment(attackerCustomer);
		mockMvc.perform(post("/api/v1/ar/payments/" + attackerPayment + "/allocate")
						.header(HttpHeaders.AUTHORIZATION, IntegrationTestSupport.bearer(attacker.token()))
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"allocations":[{"invoiceId":"%s","amount":10}]}
								""".formatted(victim.arInvoiceId())))
				.andExpect(status().is4xxClientError());
	}

	@Test
	void indirectIdor_createExpenseOnAttackerClientWithVictimCategory_blocked() throws Exception {
		UUID attackerClientId = createAttackerClient();
		mockMvc.perform(post("/api/v1/clients/" + attackerClientId + "/expenses")
						.header(HttpHeaders.AUTHORIZATION, IntegrationTestSupport.bearer(attacker.token()))
						.header(IDEMPOTENCY, UUID.randomUUID().toString())
						.contentType(MediaType.APPLICATION_JSON)
						.content(objectMapper.writeValueAsString(new CreateExpenseRequest(
								LocalDate.now(),
								victim.expenseCategoryId(),
								new BigDecimal("50.00"),
								"LKR",
								"Cross Category",
								null,
								null,
								null
						))))
				.andExpect(status().is4xxClientError());
	}

	private void assertNotFound(MockHttpServletRequestBuilder request) throws Exception {
		mockMvc.perform(request.header(HttpHeaders.AUTHORIZATION, IntegrationTestSupport.bearer(attacker.token())))
				.andExpect(status().isNotFound());
	}

	private ExpenseResponse createAttackerExpense() throws Exception {
		UUID clientId = createAttackerClient();
		var categoryResult = mockMvc.perform(post("/api/v1/categories")
						.header(HttpHeaders.AUTHORIZATION, IntegrationTestSupport.bearer(attacker.token()))
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"code":"ATK-EXP","name":"Attacker Exp","categoryType":"EXPENSE"}
								"""))
				.andExpect(status().isCreated())
				.andReturn();
		var categoryId = objectMapper.readTree(categoryResult.getResponse().getContentAsString()).get("id").asText();
		var result = mockMvc.perform(post("/api/v1/clients/" + clientId + "/expenses")
						.header(HttpHeaders.AUTHORIZATION, IntegrationTestSupport.bearer(attacker.token()))
						.header(IDEMPOTENCY, UUID.randomUUID().toString())
						.contentType(MediaType.APPLICATION_JSON)
						.content(objectMapper.writeValueAsString(new CreateExpenseRequest(
								LocalDate.now(),
								UUID.fromString(categoryId),
								new BigDecimal("10.00"),
								"LKR",
								"Attacker Vendor",
								null,
								null,
								null
						))))
				.andExpect(status().isCreated())
				.andReturn();
		return support.read(result, ExpenseResponse.class);
	}

	private UUID createAttackerClient() throws Exception {
		var result = mockMvc.perform(post("/api/v1/clients")
						.header(HttpHeaders.AUTHORIZATION, IntegrationTestSupport.bearer(attacker.token()))
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"name\":\"Attacker Client\"}"))
				.andExpect(status().isCreated())
				.andReturn();
		return UUID.fromString(objectMapper.readTree(result.getResponse().getContentAsString()).get("id").asText());
	}

	private UUID createAttackerArCustomer() throws Exception {
		String json = mockMvc.perform(post("/api/v1/ar/customers")
						.header(HttpHeaders.AUTHORIZATION, IntegrationTestSupport.bearer(attacker.token()))
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"name":"Atk Customer","email":"atk@test.com","paymentTermsDays":30}
								"""))
				.andExpect(status().isCreated())
				.andReturn().getResponse().getContentAsString();
		return UUID.fromString(objectMapper.readTree(json).get("id").asText());
	}

	private record CrossTenantReadCase(String label, MockHttpServletRequestBuilder request) {
	}

	private UUID recordAttackerPayment(UUID customerId) throws Exception {
		String json = mockMvc.perform(post("/api/v1/ar/payments")
						.header(HttpHeaders.AUTHORIZATION, IntegrationTestSupport.bearer(attacker.token()))
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"customerId":"%s","paymentDate":"2026-04-01","amount":10,"reference":"ATK"}
								""".formatted(customerId)))
				.andExpect(status().isCreated())
				.andReturn().getResponse().getContentAsString();
		return UUID.fromString(objectMapper.readTree(json).get("id").asText());
	}
}
