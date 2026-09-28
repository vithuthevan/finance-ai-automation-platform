package com.finance.platform.finance;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.finance.platform.finance.application.dto.BankAccountResponse;
import com.finance.platform.finance.application.dto.BankImportResponse;
import com.finance.platform.finance.application.dto.BankTransactionResponse;
import com.finance.platform.finance.application.dto.CategoryResponse;
import com.finance.platform.finance.application.dto.ClientResponse;
import com.finance.platform.finance.application.dto.ExpenseResponse;
import com.finance.platform.support.AbstractPostgresIntegrationTest;
import com.finance.platform.support.IntegrationTestSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MvcResult;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Slice 4 — create-from-bank duplicate protection (PostgreSQL / Testcontainers).
 */
class BankCreateFromBankSafetyIntegrationTest extends AbstractPostgresIntegrationTest {

	private static final String IDEMPOTENCY = "Idempotency-Key";

	@Autowired
	private ObjectMapper objectMapper;

	@Autowired
	private JdbcTemplate jdbcTemplate;

	private IntegrationTestSupport.Session admin;

	@BeforeEach
	void setUp() throws Exception {
		support.seedRolesIfNeeded();
		admin = support.registerFirmAdmin("Create From Bank Firm");
	}

	@Test
	void createIncomeFromBank_once_succeeds() throws Exception {
		ClientResponse client = createClientFor(admin, "Income Client");
		CategoryResponse incomeCategory = createCategoryFor(admin, "CFB-IN2", "Sales", "INCOME");
		BankAccountResponse account = createBankAccountFor(admin, client.id());
		String csv = "Date,Description,Reference,Debit,Credit,Balance\n"
				+ "2026-09-21,CLIENT PAYMENT,REF-CR,,500.00,9500.00\n";
		importCsvFor(admin, client.id(), account.id(), "credit.csv", csv);
		UUID bankTxnId = listTransactionsFor(admin, client.id(), account.id()).get(0).id();
		mockMvc.perform(post("/api/v1/clients/" + client.id() + "/bank/transactions/" + bankTxnId + "/create-income")
						.header(HttpHeaders.AUTHORIZATION, IntegrationTestSupport.bearer(admin.token()))
						.header(IDEMPOTENCY, IntegrationTestSupport.newIdempotencyKey())
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"categoryId\":\"" + incomeCategory.id() + "\"}"))
				.andExpect(status().isCreated());
		assertThat(countIncome(client.id())).isEqualTo(1);
	}

	@Test
	void createExpenseFromBank_once_succeeds() throws Exception {
		Scenario scenario = seedDebitBankLine();
		ExpenseResponse created = createExpenseFromBank(scenario, IntegrationTestSupport.newIdempotencyKey());
		assertThat(created.amount()).isEqualByComparingTo("250.00");
		assertThat(countExpenses(scenario.clientId())).isEqualTo(1);
	}

	@Test
	void sameIdempotencyKey_replaysResponse_oneExpense() throws Exception {
		Scenario scenario = seedDebitBankLine();
		String key = IntegrationTestSupport.newIdempotencyKey();
		ExpenseResponse first = createExpenseFromBank(scenario, key);
		ExpenseResponse second = createExpenseFromBank(scenario, key);
		assertThat(second.id()).isEqualTo(first.id());
		assertThat(countExpenses(scenario.clientId())).isEqualTo(1);
	}

	@Test
	void differentIdempotencyKeys_sameBankLine_oneExpense() throws Exception {
		Scenario scenario = seedDebitBankLine();
		createExpenseFromBank(scenario, IntegrationTestSupport.newIdempotencyKey());
		mockMvc.perform(post(createExpensePath(scenario))
						.header(HttpHeaders.AUTHORIZATION, IntegrationTestSupport.bearer(admin.token()))
						.header(IDEMPOTENCY, IntegrationTestSupport.newIdempotencyKey())
						.contentType(MediaType.APPLICATION_JSON)
						.content(expenseBody(scenario.categoryId())))
				.andExpect(status().isUnprocessableEntity());
		assertThat(countExpenses(scenario.clientId())).isEqualTo(1);
	}

	@Test
	void sequentialDuplicateConversion_returnsAlreadyConverted() throws Exception {
		Scenario scenario = seedDebitBankLine();
		ExpenseResponse first = createExpenseFromBank(scenario, IntegrationTestSupport.newIdempotencyKey());
		MvcResult conflict = mockMvc.perform(post(createExpensePath(scenario))
						.header(HttpHeaders.AUTHORIZATION, IntegrationTestSupport.bearer(admin.token()))
						.header(IDEMPOTENCY, IntegrationTestSupport.newIdempotencyKey())
						.contentType(MediaType.APPLICATION_JSON)
						.content(expenseBody(scenario.categoryId())))
				.andExpect(status().isUnprocessableEntity())
				.andReturn();
		JsonNode problem = objectMapper.readTree(conflict.getResponse().getContentAsString());
		assertThat(problem.get("errorCode").asText()).isEqualTo("BANK_TRANSACTION_ALREADY_CONVERTED");
		assertThat(problem.get("existingEntityId").asText()).isEqualTo(first.id().toString());
		assertThat(countExpenses(scenario.clientId())).isEqualTo(1);
	}

	@Test
	void concurrentCreateFromBank_producesSingleExpense() throws Exception {
		Scenario scenario = seedDebitBankLine();
		ExecutorService pool = Executors.newFixedThreadPool(2);
		CountDownLatch ready = new CountDownLatch(2);
		CountDownLatch start = new CountDownLatch(1);
		List<Callable<Integer>> tasks = new ArrayList<>();
		for (int i = 0; i < 2; i++) {
			tasks.add(() -> {
				ready.countDown();
				start.await();
				return mockMvc.perform(post(createExpensePath(scenario))
								.header(HttpHeaders.AUTHORIZATION, IntegrationTestSupport.bearer(admin.token()))
								.header(IDEMPOTENCY, IntegrationTestSupport.newIdempotencyKey())
								.contentType(MediaType.APPLICATION_JSON)
								.content(expenseBody(scenario.categoryId())))
						.andReturn()
						.getResponse()
						.getStatus();
			});
		}
		List<Future<Integer>> futures = new ArrayList<>();
		for (Callable<Integer> task : tasks) {
			futures.add(pool.submit(task));
		}
		ready.await();
		start.countDown();
		int created = 0;
		int rejected = 0;
		for (Future<Integer> future : futures) {
			int status = future.get();
			if (status == 201) {
				created++;
			} else if (status == 422) {
				rejected++;
			}
		}
		pool.shutdownNow();
		assertThat(created).isEqualTo(1);
		assertThat(rejected).isEqualTo(1);
		assertThat(countExpenses(scenario.clientId())).isEqualTo(1);
	}

	@Test
	void crossTypeIncomeOnDebitLine_blockedByDirection() throws Exception {
		Scenario scenario = seedDebitBankLine();
		CategoryResponse incomeCategory = createCategory("CFB-INC", "Other Income", "INCOME");
		createExpenseFromBank(scenario, IntegrationTestSupport.newIdempotencyKey());
		mockMvc.perform(post("/api/v1/clients/" + scenario.clientId() + "/bank/transactions/"
						+ scenario.bankTransactionId() + "/create-income")
						.header(HttpHeaders.AUTHORIZATION, IntegrationTestSupport.bearer(admin.token()))
						.header(IDEMPOTENCY, IntegrationTestSupport.newIdempotencyKey())
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"categoryId\":\"" + incomeCategory.id() + "\"}"))
				.andExpect(status().isUnprocessableEntity());
		assertThat(countExpenses(scenario.clientId())).isEqualTo(1);
		assertThat(countIncome(scenario.clientId())).isZero();
	}

	@Test
	void crossClientBankTransactionId_rejected() throws Exception {
		IntegrationTestSupport.Session other = support.registerFirmAdmin("Other Create Firm");
		Scenario victim = seedDebitBankLineFor(other);
		ClientResponse attackerClient = createClientFor(admin, "Attacker Client");
		CategoryResponse category = createCategoryFor(admin, "CFB-ATT", "Attacker Cat", "EXPENSE");
		mockMvc.perform(post("/api/v1/clients/" + attackerClient.id() + "/bank/transactions/"
						+ victim.bankTransactionId() + "/create-expense")
						.header(HttpHeaders.AUTHORIZATION, IntegrationTestSupport.bearer(admin.token()))
						.header(IDEMPOTENCY, IntegrationTestSupport.newIdempotencyKey())
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"categoryId\":\"" + category.id() + "\"}"))
				.andExpect(status().isNotFound());
	}

	@Test
	void conversionAudit_writtenOnce() throws Exception {
		Scenario scenario = seedDebitBankLine();
		createExpenseFromBank(scenario, IntegrationTestSupport.newIdempotencyKey());
		mockMvc.perform(post(createExpensePath(scenario))
						.header(HttpHeaders.AUTHORIZATION, IntegrationTestSupport.bearer(admin.token()))
						.header(IDEMPOTENCY, IntegrationTestSupport.newIdempotencyKey())
						.contentType(MediaType.APPLICATION_JSON)
						.content(expenseBody(scenario.categoryId())))
				.andExpect(status().isUnprocessableEntity());
		Integer audits = jdbcTemplate.queryForObject("""
				select count(*) from audit_log
				where action = 'TRANSACTION_CREATED_FROM_BANK'
				  and resource_id = ?
				""", Integer.class, scenario.bankTransactionId());
		assertThat(audits).isEqualTo(1);
	}

	private record Scenario(UUID clientId, UUID bankTransactionId, UUID categoryId) {
	}

	private Scenario seedDebitBankLine() throws Exception {
		return seedDebitBankLineFor(admin);
	}

	private Scenario seedDebitBankLineFor(IntegrationTestSupport.Session session) throws Exception {
		ClientResponse client = createClientFor(session, "Bank Client " + UUID.randomUUID().toString().substring(0, 6));
		CategoryResponse category = createCategoryFor(session, "CFB-" + UUID.randomUUID().toString().substring(0, 6), "Ops", "EXPENSE");
		BankAccountResponse account = createBankAccountFor(session, client.id());
		String csv = "Date,Description,Reference,Debit,Credit,Balance\n"
				+ "2026-09-20,OFFICE SUPPLIES,REF-1,250.00,,9000.00\n";
		importCsvFor(session, client.id(), account.id(), "line.csv", csv);
		List<BankTransactionResponse> txns = listTransactionsFor(session, client.id(), account.id());
		assertThat(txns).hasSize(1);
		return new Scenario(client.id(), txns.get(0).id(), category.id());
	}

	private ExpenseResponse createExpenseFromBank(Scenario scenario, String idempotencyKey) throws Exception {
		MvcResult result = mockMvc.perform(post(createExpensePath(scenario))
						.header(HttpHeaders.AUTHORIZATION, IntegrationTestSupport.bearer(admin.token()))
						.header(IDEMPOTENCY, idempotencyKey)
						.contentType(MediaType.APPLICATION_JSON)
						.content(expenseBody(scenario.categoryId())))
				.andExpect(status().isCreated())
				.andReturn();
		return support.read(result, ExpenseResponse.class);
	}

	private static String createExpensePath(Scenario scenario) {
		return "/api/v1/clients/" + scenario.clientId() + "/bank/transactions/"
				+ scenario.bankTransactionId() + "/create-expense";
	}

	private static String expenseBody(UUID categoryId) {
		return "{\"categoryId\":\"" + categoryId + "\",\"vendorName\":\"Vendor\",\"description\":\"From bank\"}";
	}

	private long countExpenses(UUID clientId) {
		Long count = jdbcTemplate.queryForObject("select count(*) from expenses where client_id = ?", Long.class, clientId);
		return count == null ? 0 : count;
	}

	private long countIncome(UUID clientId) {
		Long count = jdbcTemplate.queryForObject("select count(*) from income where client_id = ?", Long.class, clientId);
		return count == null ? 0 : count;
	}

	private ClientResponse createClientFor(IntegrationTestSupport.Session session, String name) throws Exception {
		MvcResult result = mockMvc.perform(post("/api/v1/clients")
						.header(HttpHeaders.AUTHORIZATION, IntegrationTestSupport.bearer(session.token()))
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"name\":\"" + name + "\"}"))
				.andExpect(status().isCreated())
				.andReturn();
		return support.read(result, ClientResponse.class);
	}

	private CategoryResponse createCategory(String code, String name, String type) throws Exception {
		return createCategoryFor(admin, code, name, type);
	}

	private CategoryResponse createCategoryFor(IntegrationTestSupport.Session session, String code, String name, String type)
			throws Exception {
		MvcResult result = mockMvc.perform(post("/api/v1/categories")
						.header(HttpHeaders.AUTHORIZATION, IntegrationTestSupport.bearer(session.token()))
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"code\":\"" + code + "\",\"name\":\"" + name + "\",\"categoryType\":\"" + type + "\"}"))
				.andExpect(status().isCreated())
				.andReturn();
		return support.read(result, CategoryResponse.class);
	}

	private BankAccountResponse createBankAccountFor(IntegrationTestSupport.Session session, UUID clientId) throws Exception {
		MvcResult result = mockMvc.perform(post("/api/v1/clients/" + clientId + "/bank/accounts")
						.header(HttpHeaders.AUTHORIZATION, IntegrationTestSupport.bearer(session.token()))
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{
								  "bankName": "Safety Bank",
								  "accountName": "Operating",
								  "maskedAccountNumber": "****2222",
								  "currency": "LKR"
								}
								"""))
				.andExpect(status().isCreated())
				.andReturn();
		return support.read(result, BankAccountResponse.class);
	}

	private BankImportResponse importCsvFor(
			IntegrationTestSupport.Session session,
			UUID clientId,
			UUID bankAccountId,
			String fileName,
			String csv
	) throws Exception {
		MockMultipartFile file = new MockMultipartFile("file", fileName, "text/csv", csv.getBytes(StandardCharsets.UTF_8));
		MvcResult result = mockMvc.perform(multipart("/api/v1/clients/" + clientId + "/bank/imports")
						.file(file)
						.param("bankAccountId", bankAccountId.toString())
						.header(HttpHeaders.AUTHORIZATION, IntegrationTestSupport.bearer(session.token()))
						.header(IDEMPOTENCY, IntegrationTestSupport.newIdempotencyKey()))
				.andExpect(status().isCreated())
				.andReturn();
		return support.read(result, BankImportResponse.class);
	}

	private List<BankTransactionResponse> listTransactionsFor(
			IntegrationTestSupport.Session session,
			UUID clientId,
			UUID bankAccountId
	) throws Exception {
		MvcResult result = mockMvc.perform(get("/api/v1/clients/" + clientId + "/bank/transactions")
						.param("bankAccountId", bankAccountId.toString())
						.param("size", "50")
						.header(HttpHeaders.AUTHORIZATION, IntegrationTestSupport.bearer(session.token())))
				.andExpect(status().isOk())
				.andReturn();
		JsonNode page = objectMapper.readTree(result.getResponse().getContentAsString());
		List<BankTransactionResponse> rows = new ArrayList<>();
		for (JsonNode node : page.get("content")) {
			rows.add(objectMapper.treeToValue(node, BankTransactionResponse.class));
		}
		return rows;
	}
}
