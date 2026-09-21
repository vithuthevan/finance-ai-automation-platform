package com.finance.platform.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.finance.platform.auth.application.dto.ClientAccessAssignmentRequest;
import com.finance.platform.auth.application.dto.CreateUserRequest;
import com.finance.platform.finance.application.dto.CategoryResponse;
import com.finance.platform.finance.application.dto.ClientResponse;
import com.finance.platform.finance.application.dto.CreateExpenseRequest;
import com.finance.platform.finance.application.dto.ExpenseResponse;
import com.finance.platform.finance.application.dto.IncomeRequest;
import com.finance.platform.finance.application.dto.IncomeResponse;
import com.finance.platform.support.AbstractPostgresIntegrationTest;
import com.finance.platform.support.IntegrationTestSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MvcResult;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Phase 1.1 — BUSINESS_OWNER and role matrix for ledger mutations vs client uploads.
 */
class LedgerAuthorizationIntegrationTest extends AbstractPostgresIntegrationTest {

	private static final String IDEMPOTENCY = "Idempotency-Key";

	@Autowired
	private ObjectMapper objectMapper;

	private IntegrationTestSupport.Session admin;
	private ClientResponse client;
	private CategoryResponse expenseCategory;
	private CategoryResponse incomeCategory;

	@BeforeEach
	void setUp() throws Exception {
		support.seedRolesIfNeeded();
		admin = support.registerFirmAdmin("Ledger Auth");
		client = createClient("Ledger Client");
		expenseCategory = createCategory("LA-EXP", "Ledger Expense", "EXPENSE");
		incomeCategory = createCategory("LA-INC", "Ledger Income", "INCOME");
	}

	@Test
	void adminCanCreateExpense() throws Exception {
		postExpense(admin.token(), status().isCreated());
	}

	@Test
	void accountantCanCreateExpense() throws Exception {
		String token = createStaff("ACCOUNTANT", List.of(client.id()));
		postExpense(token, status().isCreated());
	}

	@Test
	void auditorCannotCreateExpense() throws Exception {
		String token = createStaff("AUDITOR", List.of(client.id()));
		postExpense(token, status().isForbidden());
	}

	@Test
	void businessOwnerFullCannotCreateExpense() throws Exception {
		String token = createStaff("BUSINESS_OWNER", List.of(client.id()));
		postExpense(token, status().isForbidden());
	}

	@Test
	void businessOwnerUploadOnlyCannotCreateExpense() throws Exception {
		String token = createUploadOnlyOwner(client.id());
		postExpense(token, status().isForbidden());
	}

	@Test
	void adminCanCreateIncome() throws Exception {
		postIncome(admin.token(), status().isCreated());
	}

	@Test
	void accountantCanCreateIncome() throws Exception {
		String token = createStaff("ACCOUNTANT", List.of(client.id()));
		postIncome(token, status().isCreated());
	}

	@Test
	void auditorCannotCreateIncome() throws Exception {
		String token = createStaff("AUDITOR", List.of(client.id()));
		postIncome(token, status().isForbidden());
	}

	@Test
	void businessOwnerFullCannotCreateIncome() throws Exception {
		String token = createStaff("BUSINESS_OWNER", List.of(client.id()));
		postIncome(token, status().isForbidden());
	}

	@Test
	void businessOwnerFullCannotApproveExpense() throws Exception {
		ExpenseResponse draft = createExpenseAsAdmin();
		String ownerToken = createStaff("BUSINESS_OWNER", List.of(client.id()));
		mockMvc.perform(post("/api/v1/clients/" + client.id() + "/expenses/" + draft.id() + "/approve")
						.header(HttpHeaders.AUTHORIZATION, IntegrationTestSupport.bearer(ownerToken))
						.header(IDEMPOTENCY, UUID.randomUUID().toString()))
				.andExpect(status().isForbidden());
	}

	@Test
	void accountantCanApproveExpense() throws Exception {
		ExpenseResponse draft = createExpenseAsAdmin();
		String accountantToken = createStaff("ACCOUNTANT", List.of(client.id()));
		mockMvc.perform(post("/api/v1/clients/" + client.id() + "/expenses/" + draft.id() + "/approve")
						.header(HttpHeaders.AUTHORIZATION, IntegrationTestSupport.bearer(accountantToken))
						.header(IDEMPOTENCY, UUID.randomUUID().toString()))
				.andExpect(status().isOk());
	}

	@Test
	void auditorCannotApproveExpense() throws Exception {
		ExpenseResponse draft = createExpenseAsAdmin();
		String auditorToken = createStaff("AUDITOR", List.of(client.id()));
		mockMvc.perform(post("/api/v1/clients/" + client.id() + "/expenses/" + draft.id() + "/approve")
						.header(HttpHeaders.AUTHORIZATION, IntegrationTestSupport.bearer(auditorToken))
						.header(IDEMPOTENCY, UUID.randomUUID().toString()))
				.andExpect(status().isForbidden());
	}

	@Test
	void businessOwnerFullCannotVoidExpense() throws Exception {
		ExpenseResponse approved = approveExpenseAsAdmin(createExpenseAsAdmin());
		String ownerToken = createStaff("BUSINESS_OWNER", List.of(client.id()));
		mockMvc.perform(post("/api/v1/clients/" + client.id() + "/expenses/" + approved.id() + "/void")
						.header(HttpHeaders.AUTHORIZATION, IntegrationTestSupport.bearer(ownerToken))
						.header(IDEMPOTENCY, UUID.randomUUID().toString())
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"reason\":\"Owner void attempt\"}"))
				.andExpect(status().isForbidden());
	}

	@Test
	void businessOwnerFullCanUploadDocument() throws Exception {
		String ownerToken = createStaff("BUSINESS_OWNER", List.of(client.id()));
		byte[] png = new byte[]{
				(byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A,
				0, 0, 0, 0, 0, 0, 0, 0
		};
		MockMultipartFile file = new MockMultipartFile("file", "owner-receipt.png", "image/png", png);
		mockMvc.perform(multipart("/api/v1/clients/" + client.id() + "/documents").file(file)
						.header(HttpHeaders.AUTHORIZATION, IntegrationTestSupport.bearer(ownerToken)))
				.andExpect(status().isCreated());
	}

	@Test
	void businessOwnerUploadOnlyCanUploadDocument() throws Exception {
		String ownerToken = createUploadOnlyOwner(client.id());
		byte[] png = new byte[]{
				(byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A,
				0, 0, 0, 0, 0, 0, 0, 0
		};
		MockMultipartFile file = new MockMultipartFile("file", "upload-only.png", "image/png", png);
		mockMvc.perform(multipart("/api/v1/clients/" + client.id() + "/documents").file(file)
						.header(HttpHeaders.AUTHORIZATION, IntegrationTestSupport.bearer(ownerToken)))
				.andExpect(status().isCreated());
	}

	@Test
	void businessOwnerCannotAccessOtherFirmClientExpense() throws Exception {
		var otherFirm = support.registerFirmAdmin("Other Firm");
		ClientResponse otherClient = createClient(otherFirm.token(), "Other Client");
		CategoryResponse otherCategory = createCategory(otherFirm.token(), "OTHER-EXP", "Other Expense", "EXPENSE");
		createExpense(otherFirm.token(), otherClient.id(), otherCategory.id());

		String ownerToken = createStaff("BUSINESS_OWNER", List.of(client.id()));
		mockMvc.perform(post("/api/v1/clients/" + otherClient.id() + "/expenses")
						.header(HttpHeaders.AUTHORIZATION, IntegrationTestSupport.bearer(ownerToken))
						.contentType(MediaType.APPLICATION_JSON)
						.content(objectMapper.writeValueAsString(new CreateExpenseRequest(
								LocalDate.now(),
								otherCategory.id(),
								new BigDecimal("50.00"),
								"LKR",
								"Cross firm",
								null,
								null,
								null
						))))
				.andExpect(status().isNotFound());
	}

	private void postExpense(String token, org.springframework.test.web.servlet.ResultMatcher expected) throws Exception {
		mockMvc.perform(post("/api/v1/clients/" + client.id() + "/expenses")
						.header(HttpHeaders.AUTHORIZATION, IntegrationTestSupport.bearer(token))
						.contentType(MediaType.APPLICATION_JSON)
						.content(objectMapper.writeValueAsString(new CreateExpenseRequest(
								LocalDate.now(),
								expenseCategory.id(),
								new BigDecimal("125.00"),
								"LKR",
								"Vendor",
								"Test",
								null,
								null
						))))
				.andExpect(expected);
	}

	private void postIncome(String token, org.springframework.test.web.servlet.ResultMatcher expected) throws Exception {
		mockMvc.perform(post("/api/v1/clients/" + client.id() + "/income")
						.header(HttpHeaders.AUTHORIZATION, IntegrationTestSupport.bearer(token))
						.contentType(MediaType.APPLICATION_JSON)
						.content(objectMapper.writeValueAsString(new IncomeRequest(
								LocalDate.now(),
								incomeCategory.id(),
								new BigDecimal("200.00"),
								"LKR",
								"Customer",
								"Income test",
								null,
								null,
								null
						))))
				.andExpect(expected);
	}

	private ExpenseResponse createExpenseAsAdmin() throws Exception {
		return createExpense(admin.token(), client.id(), expenseCategory.id());
	}

	private ExpenseResponse approveExpenseAsAdmin(ExpenseResponse draft) throws Exception {
		MvcResult result = mockMvc.perform(post("/api/v1/clients/" + client.id() + "/expenses/" + draft.id() + "/approve")
						.header(HttpHeaders.AUTHORIZATION, IntegrationTestSupport.bearer(admin.token()))
						.header(IDEMPOTENCY, UUID.randomUUID().toString()))
				.andExpect(status().isOk())
				.andReturn();
		return support.read(result, ExpenseResponse.class);
	}

	private ExpenseResponse createExpense(String token, UUID clientId, UUID categoryId) throws Exception {
		MvcResult result = mockMvc.perform(post("/api/v1/clients/" + clientId + "/expenses")
						.header(HttpHeaders.AUTHORIZATION, IntegrationTestSupport.bearer(token))
						.contentType(MediaType.APPLICATION_JSON)
						.content(objectMapper.writeValueAsString(new CreateExpenseRequest(
								LocalDate.now(),
								categoryId,
								new BigDecimal("99.00"),
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

	private ClientResponse createClient(String name) throws Exception {
		return createClient(admin.token(), name);
	}

	private ClientResponse createClient(String token, String name) throws Exception {
		MvcResult result = mockMvc.perform(
						org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/api/v1/clients")
								.header(HttpHeaders.AUTHORIZATION, IntegrationTestSupport.bearer(token))
								.contentType(MediaType.APPLICATION_JSON)
								.content("{\"name\":\"" + name + "\"}"))
				.andExpect(status().isCreated())
				.andReturn();
		return support.read(result, ClientResponse.class);
	}

	private CategoryResponse createCategory(String code, String name, String type) throws Exception {
		return createCategory(admin.token(), code, name, type);
	}

	private CategoryResponse createCategory(String token, String code, String name, String type) throws Exception {
		MvcResult result = mockMvc.perform(
						org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/api/v1/categories")
								.header(HttpHeaders.AUTHORIZATION, IntegrationTestSupport.bearer(token))
								.contentType(MediaType.APPLICATION_JSON)
								.content("""
										{"code":"%s","name":"%s","categoryType":"%s"}
										""".formatted(code, name, type)))
				.andExpect(status().isCreated())
				.andReturn();
		return support.read(result, CategoryResponse.class);
	}

	private String createStaff(String role, List<UUID> clientIds) throws Exception {
		String suffix = IntegrationTestSupport.uniqueSuffix();
		String email = role.toLowerCase() + "-la-" + suffix + "@example.com";
		mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/api/v1/users")
						.header(HttpHeaders.AUTHORIZATION, IntegrationTestSupport.bearer(admin.token()))
						.contentType(MediaType.APPLICATION_JSON)
						.content(objectMapper.writeValueAsString(new CreateUserRequest(
								email,
								IntegrationTestSupport.PASSWORD,
								role + " User",
								role,
								clientIds
						))))
				.andExpect(status().isCreated());
		return support.login(email);
	}

	private String createUploadOnlyOwner(UUID clientId) throws Exception {
		String suffix = IntegrationTestSupport.uniqueSuffix();
		String email = "upload-only-la-" + suffix + "@example.com";
		mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/api/v1/users")
						.header(HttpHeaders.AUTHORIZATION, IntegrationTestSupport.bearer(admin.token()))
						.contentType(MediaType.APPLICATION_JSON)
						.content(objectMapper.writeValueAsString(new CreateUserRequest(
								email,
								IntegrationTestSupport.PASSWORD,
								"Upload Only Owner",
								"BUSINESS_OWNER",
								null,
								List.of(new ClientAccessAssignmentRequest(clientId, "UPLOAD_ONLY"))
						))))
				.andExpect(status().isCreated());
		return support.login(email);
	}
}
