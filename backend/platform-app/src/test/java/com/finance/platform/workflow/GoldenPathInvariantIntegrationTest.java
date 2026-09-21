package com.finance.platform.workflow;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.finance.platform.auth.application.dto.CreateUserRequest;
import com.finance.platform.finance.application.dto.BankAccountResponse;
import com.finance.platform.finance.application.dto.BankImportPreviewResponse;
import com.finance.platform.finance.application.dto.BankImportResponse;
import com.finance.platform.finance.application.dto.BankTransactionResponse;
import com.finance.platform.finance.domain.model.BankTransaction;
import com.finance.platform.finance.application.dto.CategoryResponse;
import com.finance.platform.finance.application.dto.ClientResponse;
import com.finance.platform.finance.application.dto.CreateExpenseRequest;
import com.finance.platform.finance.application.dto.ExpenseResponse;
import com.finance.platform.finance.application.dto.PeriodResponse;
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
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * High-value negative scenarios for the golden-path financial workflow.
 * Cross-tenant isolation is covered by {@link com.finance.platform.security.TenantIsolationIntegrationTest}.
 */
class GoldenPathInvariantIntegrationTest extends AbstractPostgresIntegrationTest {

	private static final String IDEMPOTENCY = "Idempotency-Key";

	@Autowired
	private ObjectMapper objectMapper;

	private IntegrationTestSupport.Session admin;

	@BeforeEach
	void setUp() throws Exception {
		support.seedRolesIfNeeded();
		admin = support.registerFirmAdmin("Invariant Firm");
	}

	@Test
	void duplicateApproveWithSameIdempotencyKey_doesNotDuplicateApproval() throws Exception {
		ClientResponse client = createClient("Idempotent Client");
		CategoryResponse category = createCategory("IDEM-EXP", "Idempotent Expense", "EXPENSE");
		ExpenseResponse draft = createExpense(client.id(), category.id(), "1500.00", "Idem Vendor");
		String key = UUID.randomUUID().toString();

		MvcResult first = mockMvc.perform(post("/api/v1/clients/" + client.id() + "/expenses/" + draft.id() + "/approve")
						.header(HttpHeaders.AUTHORIZATION, IntegrationTestSupport.bearer(admin.token()))
						.header(IDEMPOTENCY, key))
				.andExpect(status().isOk())
				.andReturn();
		ExpenseResponse approved = support.read(first, ExpenseResponse.class);
		assertThat(approved.status()).isEqualTo("APPROVED");

		MvcResult second = mockMvc.perform(post("/api/v1/clients/" + client.id() + "/expenses/" + draft.id() + "/approve")
						.header(HttpHeaders.AUTHORIZATION, IntegrationTestSupport.bearer(admin.token()))
						.header(IDEMPOTENCY, key))
				.andExpect(status().isOk())
				.andReturn();
		ExpenseResponse replay = support.read(second, ExpenseResponse.class);
		assertThat(replay.id()).isEqualTo(approved.id());
		assertThat(replay.status()).isEqualTo("APPROVED");

		MvcResult list = mockMvc.perform(get("/api/v1/clients/" + client.id() + "/expenses")
						.param("status", "APPROVED")
						.param("size", "50")
						.header(HttpHeaders.AUTHORIZATION, IntegrationTestSupport.bearer(admin.token())))
				.andExpect(status().isOk())
				.andReturn();
		JsonNode page = objectMapper.readTree(list.getResponse().getContentAsString());
		long approvedCount = page.get("content").findValues("id").stream()
				.filter(node -> approved.id().toString().equals(node.asText()))
				.count();
		assertThat(approvedCount).isEqualTo(1);
	}

	@Test
	void closedPeriod_rejectsNewExpenseInPeriod() throws Exception {
		ClientResponse client = createClient("Closed Period Client");
		CategoryResponse category = createCategory("CLOSE-EXP", "Close Expense", "EXPENSE");
		LocalDate txnDate = LocalDate.now().withDayOfMonth(10);

		ExpenseResponse draft = createExpense(client.id(), category.id(), "2500.00", "Close Vendor", txnDate);
		approveExpense(client.id(), draft.id());

		PeriodResponse period = getOrCreatePeriod(client.id(), txnDate.getYear(), txnDate.getMonthValue());
		closePeriod(client.id(), period.id());

		mockMvc.perform(post("/api/v1/clients/" + client.id() + "/expenses")
						.header(HttpHeaders.AUTHORIZATION, IntegrationTestSupport.bearer(admin.token()))
						.contentType(MediaType.APPLICATION_JSON)
						.content(objectMapper.writeValueAsString(new CreateExpenseRequest(
								txnDate,
								category.id(),
								new BigDecimal("100.00"),
								"LKR",
								"Blocked Vendor",
								null,
								null,
								null
						))))
				.andExpect(status().isUnprocessableEntity());
	}

	@Test
	void invalidDocumentUpload_rejected() throws Exception {
		ClientResponse client = createClient("Upload Client");
		MockMultipartFile file = new MockMultipartFile("file", "malware.exe", "application/octet-stream", new byte[]{1, 2, 3});
		mockMvc.perform(multipart("/api/v1/clients/" + client.id() + "/documents")
						.file(file)
						.header(HttpHeaders.AUTHORIZATION, IntegrationTestSupport.bearer(admin.token())))
				.andExpect(status().isBadRequest());
	}

	@Test
	void reconciliationCannotConfirmTwice() throws Exception {
		ClientResponse client = createClient("Reconcile Client");
		CategoryResponse category = createCategory("RECON-EXP", "Reconcile Expense", "EXPENSE");
		LocalDate txnDate = LocalDate.now().withDayOfMonth(12);
		BigDecimal amount = new BigDecimal("3200.00");

		ExpenseResponse approved = approveExpense(
				client.id(),
				createExpense(client.id(), category.id(), amount.toPlainString(), "Reconcile Vendor", txnDate).id());
		BankAccountResponse account = createBankAccount(client.id());
		importBankCsv(client.id(), account.id(), txnDate, amount);
		BankTransactionResponse bankTxn = listBankTransactions(client.id(), account.id()).get(0);

		confirmReconciliation(client.id(), bankTxn.id(), approved.id(), UUID.randomUUID().toString());

		mockMvc.perform(post("/api/v1/clients/" + client.id() + "/bank/transactions/" + bankTxn.id() + "/confirm")
						.header(HttpHeaders.AUTHORIZATION, IntegrationTestSupport.bearer(admin.token()))
						.header(IDEMPOTENCY, UUID.randomUUID().toString())
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"expenseId\":\"" + approved.id() + "\"}"))
				.andExpect(status().isUnprocessableEntity());
	}

	@Test
	void bankCsvImport_assignsOwnership_previewDoesNotPersist_duplicateRejected() throws Exception {
		ClientResponse client = createClient("Bank Import Client");
		BankAccountResponse account = createBankAccount(client.id());
		LocalDate txnDate = LocalDate.now().withDayOfMonth(8);
		String csv = "Date,Description,Reference,Debit,Credit,Balance\n"
				+ txnDate + ",KEELLS SUPER KANDY,REF1001,4850.00,,125150.00\n";
		MockMultipartFile file = new MockMultipartFile(
				"file", "bank-sept.csv", "text/csv", csv.getBytes(StandardCharsets.UTF_8));

		MvcResult previewResult = mockMvc.perform(multipart("/api/v1/clients/" + client.id() + "/bank/imports/preview")
						.file(file)
						.param("bankAccountId", account.id().toString())
						.header(HttpHeaders.AUTHORIZATION, IntegrationTestSupport.bearer(admin.token())))
				.andExpect(status().isOk())
				.andReturn();
		BankImportPreviewResponse preview = support.read(previewResult, BankImportPreviewResponse.class);
		assertThat(preview.validRows()).isEqualTo(1);
		assertThat(listBankTransactions(client.id(), account.id())).isEmpty();

		MvcResult importResult = mockMvc.perform(multipart("/api/v1/clients/" + client.id() + "/bank/imports")
						.file(file)
						.param("bankAccountId", account.id().toString())
						.header(HttpHeaders.AUTHORIZATION, IntegrationTestSupport.bearer(admin.token()))
						.header(IDEMPOTENCY, UUID.randomUUID().toString()))
				.andExpect(status().isCreated())
				.andReturn();
		BankImportResponse imported = support.read(importResult, BankImportResponse.class);
		assertThat(imported.importedCount()).isEqualTo(1);
		assertThat(imported.clientId()).isEqualTo(client.id());
		assertThat(imported.bankAccountId()).isEqualTo(account.id());

		List<BankTransactionResponse> rows = listBankTransactions(client.id(), account.id());
		assertThat(rows).hasSize(1);
		assertThat(rows.get(0).clientId()).isEqualTo(client.id());
		assertThat(rows.get(0).bankAccountId()).isEqualTo(account.id());
		assertThat(rows.get(0).description()).isEqualTo("KEELLS SUPER KANDY");
		assertThat(rows.get(0).debit()).isEqualByComparingTo("4850.00");
		assertThat(rows.get(0).matchStatus()).isIn(
				BankTransaction.MatchStatus.UNMATCHED, BankTransaction.MatchStatus.SUGGESTED);
		assertThat(rows.get(0).matchStatus()).isNotEqualTo(BankTransaction.MatchStatus.MATCHED);

		mockMvc.perform(get("/api/v1/clients/" + client.id() + "/bank/transactions")
						.param("size", "50")
						.header(HttpHeaders.AUTHORIZATION, IntegrationTestSupport.bearer(admin.token())))
				.andExpect(status().isOk());

		mockMvc.perform(multipart("/api/v1/clients/" + client.id() + "/bank/imports")
						.file(file)
						.param("bankAccountId", account.id().toString())
						.header(HttpHeaders.AUTHORIZATION, IntegrationTestSupport.bearer(admin.token()))
						.header(IDEMPOTENCY, UUID.randomUUID().toString()))
				.andExpect(status().isUnprocessableEntity());

		assertThat(listBankTransactions(client.id(), account.id())).hasSize(1);
	}

	@Test
	void businessOwnerCannotApproveExpense() throws Exception {
		ClientResponse client = createClient("Owner Client");
		CategoryResponse category = createCategory("OWNER-EXP", "Owner Expense", "EXPENSE");
		String ownerToken = createStaffAndLogin("BUSINESS_OWNER", List.of(client.id()));
		ExpenseResponse draft = createExpense(client.id(), category.id(), "900.00", "Owner Vendor");

		mockMvc.perform(post("/api/v1/clients/" + client.id() + "/expenses/" + draft.id() + "/approve")
						.header(HttpHeaders.AUTHORIZATION, IntegrationTestSupport.bearer(ownerToken))
						.header(IDEMPOTENCY, UUID.randomUUID().toString()))
				.andExpect(status().isForbidden());
	}

	@Test
	void businessOwnerCannotCreateExpense() throws Exception {
		ClientResponse client = createClient("Owner Create Client");
		CategoryResponse category = createCategory("OWNER-CREATE", "Owner Create Expense", "EXPENSE");
		String ownerToken = createStaffAndLogin("BUSINESS_OWNER", List.of(client.id()));

		mockMvc.perform(post("/api/v1/clients/" + client.id() + "/expenses")
						.header(HttpHeaders.AUTHORIZATION, IntegrationTestSupport.bearer(ownerToken))
						.contentType(MediaType.APPLICATION_JSON)
						.content(objectMapper.writeValueAsString(new CreateExpenseRequest(
								LocalDate.now(),
								category.id(),
								new BigDecimal("100.00"),
								"LKR",
								"Owner Vendor",
								"Blocked",
								null,
								null
						))))
				.andExpect(status().isForbidden());
	}

	@Test
	void voidedExpenseCannotBeApprovedAgain() throws Exception {
		ClientResponse client = createClient("Void Client");
		CategoryResponse category = createCategory("VOID-EXP", "Void Expense", "EXPENSE");
		ExpenseResponse draft = createExpense(client.id(), category.id(), "700.00", "Void Vendor");
		ExpenseResponse approved = approveExpense(client.id(), draft.id());

		mockMvc.perform(post("/api/v1/clients/" + client.id() + "/expenses/" + approved.id() + "/void")
						.header(HttpHeaders.AUTHORIZATION, IntegrationTestSupport.bearer(admin.token()))
						.header(IDEMPOTENCY, UUID.randomUUID().toString())
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"reason\":\"Correction\"}"))
				.andExpect(status().isOk());

		mockMvc.perform(post("/api/v1/clients/" + client.id() + "/expenses/" + approved.id() + "/approve")
						.header(HttpHeaders.AUTHORIZATION, IntegrationTestSupport.bearer(admin.token()))
						.header(IDEMPOTENCY, UUID.randomUUID().toString()))
				.andExpect(status().isUnprocessableEntity());
	}

	private ClientResponse createClient(String name) throws Exception {
		MvcResult result = mockMvc.perform(post("/api/v1/clients")
						.header(HttpHeaders.AUTHORIZATION, IntegrationTestSupport.bearer(admin.token()))
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"name\":\"" + name + "\"}"))
				.andExpect(status().isCreated())
				.andReturn();
		return support.read(result, ClientResponse.class);
	}

	private CategoryResponse createCategory(String code, String name, String type) throws Exception {
		MvcResult result = mockMvc.perform(post("/api/v1/categories")
						.header(HttpHeaders.AUTHORIZATION, IntegrationTestSupport.bearer(admin.token()))
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"code":"%s","name":"%s","categoryType":"%s"}
								""".formatted(code, name, type)))
				.andExpect(status().isCreated())
				.andReturn();
		return support.read(result, CategoryResponse.class);
	}

	private ExpenseResponse createExpense(UUID clientId, UUID categoryId, String amount, String vendor) throws Exception {
		return createExpense(clientId, categoryId, amount, vendor, LocalDate.now());
	}

	private ExpenseResponse createExpense(UUID clientId, UUID categoryId, String amount, String vendor, LocalDate date)
			throws Exception {
		MvcResult result = mockMvc.perform(post("/api/v1/clients/" + clientId + "/expenses")
						.header(HttpHeaders.AUTHORIZATION, IntegrationTestSupport.bearer(admin.token()))
						.contentType(MediaType.APPLICATION_JSON)
						.content(objectMapper.writeValueAsString(new CreateExpenseRequest(
								date,
								categoryId,
								new BigDecimal(amount),
								"LKR",
								vendor,
								null,
								null,
								null
						))))
				.andExpect(status().isCreated())
				.andReturn();
		return support.read(result, ExpenseResponse.class);
	}

	private ExpenseResponse approveExpense(UUID clientId, UUID expenseId) throws Exception {
		MvcResult result = mockMvc.perform(post("/api/v1/clients/" + clientId + "/expenses/" + expenseId + "/approve")
						.header(HttpHeaders.AUTHORIZATION, IntegrationTestSupport.bearer(admin.token()))
						.header(IDEMPOTENCY, UUID.randomUUID().toString()))
				.andExpect(status().isOk())
				.andReturn();
		return support.read(result, ExpenseResponse.class);
	}

	private String createStaffAndLogin(String role, List<UUID> clientIds) throws Exception {
		String suffix = IntegrationTestSupport.uniqueSuffix();
		String email = role.toLowerCase() + "-" + suffix + "@example.com";
		mockMvc.perform(post("/api/v1/users")
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

	private BankAccountResponse createBankAccount(UUID clientId) throws Exception {
		MvcResult result = mockMvc.perform(post("/api/v1/clients/" + clientId + "/bank/accounts")
						.header(HttpHeaders.AUTHORIZATION, IntegrationTestSupport.bearer(admin.token()))
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{
								  "bankName": "Invariant Bank",
								  "accountName": "Operating",
								  "maskedAccountNumber": "****9999",
								  "currency": "LKR"
								}
								"""))
				.andExpect(status().isCreated())
				.andReturn();
		return support.read(result, BankAccountResponse.class);
	}

	private void importBankCsv(UUID clientId, UUID bankAccountId, LocalDate txnDate, BigDecimal amount) throws Exception {
		String csv = "Date,Description,Reference,Debit,Credit,Balance\n"
				+ txnDate + ",VENDOR PAYMENT,REF2001," + amount + ",,50000.00\n";
		MockMultipartFile file = new MockMultipartFile(
				"file", "bank.csv", "text/csv", csv.getBytes(StandardCharsets.UTF_8));
		mockMvc.perform(multipart("/api/v1/clients/" + clientId + "/bank/imports")
						.file(file)
						.param("bankAccountId", bankAccountId.toString())
						.header(HttpHeaders.AUTHORIZATION, IntegrationTestSupport.bearer(admin.token()))
						.header(IDEMPOTENCY, UUID.randomUUID().toString()))
				.andExpect(status().isCreated());
	}

	private List<BankTransactionResponse> listBankTransactions(UUID clientId, UUID bankAccountId) throws Exception {
		MvcResult result = mockMvc.perform(get("/api/v1/clients/" + clientId + "/bank/transactions")
						.param("bankAccountId", bankAccountId.toString())
						.param("size", "20")
						.header(HttpHeaders.AUTHORIZATION, IntegrationTestSupport.bearer(admin.token())))
				.andExpect(status().isOk())
				.andReturn();
		JsonNode page = objectMapper.readTree(result.getResponse().getContentAsString());
		List<BankTransactionResponse> rows = new java.util.ArrayList<>();
		for (JsonNode row : page.get("content")) {
			rows.add(objectMapper.treeToValue(row, BankTransactionResponse.class));
		}
		return rows;
	}

	private void confirmReconciliation(UUID clientId, UUID bankTransactionId, UUID expenseId, String idempotencyKey)
			throws Exception {
		mockMvc.perform(post("/api/v1/clients/" + clientId + "/bank/transactions/" + bankTransactionId + "/confirm")
						.header(HttpHeaders.AUTHORIZATION, IntegrationTestSupport.bearer(admin.token()))
						.header(IDEMPOTENCY, idempotencyKey)
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"expenseId\":\"" + expenseId + "\"}"))
				.andExpect(status().isOk());
	}

	private PeriodResponse getOrCreatePeriod(UUID clientId, int year, int month) throws Exception {
		MvcResult result = mockMvc.perform(post("/api/v1/clients/" + clientId + "/periods")
						.param("year", String.valueOf(year))
						.param("month", String.valueOf(month))
						.header(HttpHeaders.AUTHORIZATION, IntegrationTestSupport.bearer(admin.token())))
				.andExpect(status().isOk())
				.andReturn();
		return support.read(result, PeriodResponse.class);
	}

	private PeriodResponse closePeriod(UUID clientId, UUID periodId) throws Exception {
		MvcResult result = mockMvc.perform(post("/api/v1/clients/" + clientId + "/periods/" + periodId + "/close")
						.header(HttpHeaders.AUTHORIZATION, IntegrationTestSupport.bearer(admin.token()))
						.header(IDEMPOTENCY, UUID.randomUUID().toString())
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"closeNote\":\"Invariant close\"}"))
				.andExpect(status().isOk())
				.andReturn();
		return support.read(result, PeriodResponse.class);
	}
}
