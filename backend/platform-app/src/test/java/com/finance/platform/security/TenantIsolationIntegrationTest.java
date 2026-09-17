package com.finance.platform.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.finance.platform.finance.application.dto.CategoryResponse;
import com.finance.platform.finance.application.dto.ClientResponse;
import com.finance.platform.finance.application.dto.CreateExpenseRequest;
import com.finance.platform.finance.application.dto.DocumentResponse;
import com.finance.platform.finance.application.dto.ExpenseResponse;
import com.finance.platform.support.AbstractPostgresIntegrationTest;
import com.finance.platform.support.IntegrationTestSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class TenantIsolationIntegrationTest extends AbstractPostgresIntegrationTest {

	@Autowired
	private ObjectMapper objectMapper;

	@BeforeEach
	void setUp() {
		support.seedRolesIfNeeded();
	}

	@Test
	void firmACanAccessOwnClient() throws Exception {
		var firmA = support.registerFirmAdmin("Tenant A");
		ClientResponse client = createClient(firmA.token(), "Client A");

		mockMvc.perform(get("/api/v1/clients/" + client.id())
						.header(HttpHeaders.AUTHORIZATION, IntegrationTestSupport.bearer(firmA.token())))
				.andExpect(status().isOk());
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
	void firmACannotAccessFirmBExpense() throws Exception {
		var firmA = support.registerFirmAdmin("Expense Iso A");
		var firmB = support.registerFirmAdmin("Expense Iso B");

		ClientResponse clientB = createClient(firmB.token(), "Client B");
		CategoryResponse categoryB = createCategory(firmB.token(), "TRAVEL", "Travel", "EXPENSE");
		ExpenseResponse expenseB = createExpense(firmB.token(), clientB.id(), categoryB.id());

		mockMvc.perform(get("/api/v1/clients/" + clientB.id() + "/expenses/" + expenseB.id())
						.header(HttpHeaders.AUTHORIZATION, IntegrationTestSupport.bearer(firmA.token())))
				.andExpect(status().isNotFound());
	}

	@Test
	void firmAdminCannotAccessFirmBClientEvenWithKnownIds() throws Exception {
		var firmA = support.registerFirmAdmin("Admin Block A");
		var firmB = support.registerFirmAdmin("Admin Block B");

		ClientResponse clientB = createClient(firmB.token(), "Protected Client");

		mockMvc.perform(get("/api/v1/clients/" + clientB.id())
						.header(HttpHeaders.AUTHORIZATION, IntegrationTestSupport.bearer(firmA.token())))
				.andExpect(status().isNotFound());
	}

	@Test
	void extraFirmIdInCategoryBody_isIgnored() throws Exception {
		var firmA = support.registerFirmAdmin("Category Iso A");
		var firmB = support.registerFirmAdmin("Category Iso B");

		var result = mockMvc.perform(post("/api/v1/categories")
						.header(HttpHeaders.AUTHORIZATION, IntegrationTestSupport.bearer(firmA.token()))
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{
								  "code": "OFFICE",
								  "name": "Office",
								  "categoryType": "EXPENSE",
								  "firmId": "%s"
								}
								""".formatted(firmB.firmId())))
				.andExpect(status().isCreated())
				.andReturn();

		CategoryResponse body = support.read(result, CategoryResponse.class);
		assertThat(body.firmId()).isEqualTo(firmA.firmId());
		assertThat(body.firmId()).isNotEqualTo(firmB.firmId());
	}

	@Test
	void firmACannotDownloadFirmBDocument() throws Exception {
		var firmA = support.registerFirmAdmin("Doc Iso A");
		var firmB = support.registerFirmAdmin("Doc Iso B");

		ClientResponse clientB = createClient(firmB.token(), "Doc Client B");
		DocumentResponse documentB = uploadDocument(firmB.token(), clientB.id());

		mockMvc.perform(get("/api/v1/clients/" + clientB.id() + "/documents/" + documentB.id() + "/content")
						.header(HttpHeaders.AUTHORIZATION, IntegrationTestSupport.bearer(firmA.token())))
				.andExpect(status().isNotFound());
	}

	@Test
	void firmACannotImportBankCsvForFirmBClient() throws Exception {
		var firmA = support.registerFirmAdmin("Bank Iso A");
		var firmB = support.registerFirmAdmin("Bank Iso B");
		ClientResponse clientB = createClient(firmB.token(), "Bank Client B");
		var accountResult = mockMvc.perform(post("/api/v1/clients/" + clientB.id() + "/bank/accounts")
						.header(HttpHeaders.AUTHORIZATION, IntegrationTestSupport.bearer(firmB.token()))
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{
								  "bankName": "Iso Bank",
								  "accountName": "Operating",
								  "maskedAccountNumber": "****0001",
								  "currency": "LKR"
								}
								"""))
				.andExpect(status().isCreated())
				.andReturn();
		UUID accountId = UUID.fromString(
				objectMapper.readTree(accountResult.getResponse().getContentAsString()).get("id").asText());
		MockMultipartFile file = new MockMultipartFile(
				"file",
				"bank.csv",
				"text/csv",
				"Date,Description,Reference,Debit,Credit,Balance\n2026-09-05,KEELLS,REF1,100.00,,1.00\n".getBytes());

		mockMvc.perform(multipart("/api/v1/clients/" + clientB.id() + "/bank/imports")
						.file(file)
						.param("bankAccountId", accountId.toString())
						.header(HttpHeaders.AUTHORIZATION, IntegrationTestSupport.bearer(firmA.token()))
						.header("Idempotency-Key", UUID.randomUUID().toString()))
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

	private CategoryResponse createCategory(String token, String code, String name, String type) throws Exception {
		var result = mockMvc.perform(post("/api/v1/categories")
						.header(HttpHeaders.AUTHORIZATION, IntegrationTestSupport.bearer(token))
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"code":"%s","name":"%s","categoryType":"%s"}
								""".formatted(code, name, type)))
				.andExpect(status().isCreated())
				.andReturn();
		return support.read(result, CategoryResponse.class);
	}

	private ExpenseResponse createExpense(String token, UUID clientId, UUID categoryId) throws Exception {
		var result = mockMvc.perform(post("/api/v1/clients/" + clientId + "/expenses")
						.header(HttpHeaders.AUTHORIZATION, IntegrationTestSupport.bearer(token))
						.header("Idempotency-Key", UUID.randomUUID().toString())
						.contentType(MediaType.APPLICATION_JSON)
						.content(objectMapper.writeValueAsString(new CreateExpenseRequest(
								LocalDate.now(),
								categoryId,
								new BigDecimal("100.00"),
								"LKR",
								"Vendor",
								null,
								null,
								null
						))))
				.andExpect(status().isCreated())
				.andReturn();
		return support.read(result, ExpenseResponse.class);
	}

	private DocumentResponse uploadDocument(String token, UUID clientId) throws Exception {
		byte[] png = new byte[]{
				(byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A,
				0, 0, 0, 0, 0, 0, 0, 0
		};
		MockMultipartFile file = new MockMultipartFile("file", "receipt.png", "image/png", png);
		var result = mockMvc.perform(multipart("/api/v1/clients/" + clientId + "/documents").file(file)
						.header(HttpHeaders.AUTHORIZATION, IntegrationTestSupport.bearer(token)))
				.andExpect(status().isCreated())
				.andReturn();
		return support.read(result, DocumentResponse.class);
	}
}
