package com.finance.platform.finance;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.finance.platform.core.idempotency.IdempotencyBeginResult;
import com.finance.platform.core.idempotency.IdempotencyFilter;
import com.finance.platform.core.idempotency.IdempotencyService;
import com.finance.platform.core.security.TenantContext;
import com.finance.platform.core.security.TenantContextHolder;
import com.finance.platform.core.security.UserRole;
import com.finance.platform.auth.application.dto.CreateUserRequest;
import com.finance.platform.finance.application.dto.BankAccountResponse;
import com.finance.platform.finance.application.dto.BankTransactionResponse;
import com.finance.platform.finance.application.dto.CategoryResponse;
import com.finance.platform.finance.application.dto.ClientResponse;
import com.finance.platform.finance.application.dto.ExpenseResponse;
import com.finance.platform.support.AbstractPostgresIntegrationTest;
import com.finance.platform.support.IntegrationTestSupport;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MvcResult;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Slice 5 — database-backed idempotency (PostgreSQL / Testcontainers).
 */
class IdempotencyFrameworkIntegrationTest extends AbstractPostgresIntegrationTest {

	private static final String IDEMPOTENCY = "Idempotency-Key";

	@Autowired
	private ObjectMapper objectMapper;

	@Autowired
	private JdbcTemplate jdbcTemplate;

	@Autowired
	private IdempotencyService idempotencyService;

	@Autowired
	private IdempotencyFilter idempotencyFilter;

	private IntegrationTestSupport.Session admin;
	private ClientResponse client;
	private CategoryResponse expenseCategory;
	private CategoryResponse incomeCategory;

	@BeforeEach
	void setUp() throws Exception {
		support.seedRolesIfNeeded();
		admin = support.registerFirmAdmin("Idempotency Firm");
		client = createClient(admin, "Idem Client");
		expenseCategory = createCategory(admin, "IDM-" + suffix(), "Ops", "EXPENSE");
		incomeCategory = createCategory(admin, "IDMI-" + suffix(), "Sales", "INCOME");
	}

	@Test
	void sameKeyAndSameRequest_replays_andCreatesOneExpense() throws Exception {
		String key = "replay-" + UUID.randomUUID();
		String body = expenseJson("1000.00", "Vendor A");
		MvcResult first = postExpense(client.id(), key, body, null).andExpect(status().isCreated()).andReturn();
		MvcResult second = postExpense(client.id(), key, body, null).andExpect(status().isCreated()).andReturn();
		ExpenseResponse created = support.read(first, ExpenseResponse.class);
		ExpenseResponse replayed = support.read(second, ExpenseResponse.class);
		assertThat(replayed.id()).isEqualTo(created.id());
		assertThat(second.getResponse().getStatus()).isEqualTo(201);
		assertThat(second.getResponse().getContentType()).contains("application/json");
		assertThat(second.getResponse().getContentAsString()).isEqualTo(first.getResponse().getContentAsString());
		assertThat(countExpenses(client.id())).isEqualTo(1);
	}

	@Test
	void sameKeyAndDifferentBody_isKeyReuseConflict() throws Exception {
		String key = "mismatch-" + UUID.randomUUID();
		postExpense(client.id(), key, expenseJson("1000.00", "Vendor A"), null).andExpect(status().isCreated());
		MvcResult conflict = postExpense(client.id(), key, expenseJson("9000.00", "Vendor A"), null)
				.andExpect(status().isConflict())
				.andReturn();
		assertThat(errorCode(conflict)).isEqualTo("IDEMPOTENCY_KEY_REUSED_WITH_DIFFERENT_REQUEST");
		assertThat(countExpenses(client.id())).isEqualTo(1);
	}

	@Test
	void sameKeyOnDifferentClientPath_doesNotReplay() throws Exception {
		ClientResponse other = createClient(admin, "Other Client");
		String key = "path-" + UUID.randomUUID();
		String body = expenseJson("1000.00", "Vendor A");
		postExpense(client.id(), key, body, null).andExpect(status().isCreated());
		MvcResult conflict = postExpense(other.id(), key, body, null).andExpect(status().isConflict()).andReturn();
		assertThat(errorCode(conflict)).isEqualTo("IDEMPOTENCY_KEY_REUSED_WITH_DIFFERENT_REQUEST");
		assertThat(countExpenses(client.id())).isEqualTo(1);
		assertThat(countExpenses(other.id())).isZero();
	}

	@Test
	void sameKeyAcrossExpenseAndIncome_doesNotReplay() throws Exception {
		String key = "operation-" + UUID.randomUUID();
		postExpense(client.id(), key, expenseJson("1000.00", "Vendor A"), null).andExpect(status().isCreated());
		MvcResult conflict = mockMvc.perform(post("/api/v1/clients/" + client.id() + "/income")
						.header(HttpHeaders.AUTHORIZATION, IntegrationTestSupport.bearer(admin.token()))
						.header(IDEMPOTENCY, key)
						.contentType(MediaType.APPLICATION_JSON)
						.content(incomeJson("1000.00")))
				.andExpect(status().isConflict())
				.andReturn();
		assertThat(errorCode(conflict)).isEqualTo("IDEMPOTENCY_KEY_REUSED_WITH_DIFFERENT_REQUEST");
		assertThat(countExpenses(client.id())).isEqualTo(1);
		assertThat(countIncome(client.id())).isZero();
	}

	@Test
	void sameRawKey_isIndependentPerTenant() throws Exception {
		IntegrationTestSupport.Session otherFirm = support.registerFirmAdmin("Idempotency Other Firm");
		ClientResponse otherClient = createClient(otherFirm, "Tenant B Client");
		CategoryResponse otherCategory = createCategory(otherFirm, "IDMB-" + suffix(), "Ops", "EXPENSE");
		String key = "shared-" + UUID.randomUUID();
		String bodyA = expenseJsonFor(expenseCategory.id(), "1000.00", "Vendor A");
		String bodyB = expenseJsonFor(otherCategory.id(), "1000.00", "Vendor A");
		postExpense(admin, client.id(), key, bodyA).andExpect(status().isCreated());
		postExpense(otherFirm, otherClient.id(), key, bodyB).andExpect(status().isCreated());
		assertThat(countExpenses(client.id())).isEqualTo(1);
		assertThat(countExpenses(otherClient.id())).isEqualTo(1);
		Integer rows = jdbcTemplate.queryForObject(
				"select count(*) from idempotency_keys where firm_id in (?, ?)",
				Integer.class,
				admin.firmId(),
				otherFirm.firmId());
		assertThat(rows).isEqualTo(2);
	}

	@Test
	void sameRawKey_isIndependentPerUserInSameTenant() throws Exception {
		String key = "user-scope-" + UUID.randomUUID();
		String body = expenseJson("1000.00", "Vendor A");
		MvcResult adminResult = postExpense(client.id(), key, body, null).andExpect(status().isCreated()).andReturn();
		String email = "acct-" + suffix() + "@example.com";
		mockMvc.perform(post("/api/v1/users")
						.header(HttpHeaders.AUTHORIZATION, IntegrationTestSupport.bearer(admin.token()))
						.contentType(MediaType.APPLICATION_JSON)
						.content(objectMapper.writeValueAsString(new CreateUserRequest(
								email,
								IntegrationTestSupport.PASSWORD,
								"Accountant User",
								"ACCOUNTANT",
								List.of(client.id())))))
				.andExpect(status().isCreated());
		IntegrationTestSupport.Session accountant = new IntegrationTestSupport.Session(
				admin.firmId(), null, email, support.login(email));
		MvcResult accountantResult = postExpense(accountant, client.id(), key, body)
				.andExpect(status().isCreated())
				.andReturn();
		assertThat(support.read(accountantResult, ExpenseResponse.class).id())
				.isNotEqualTo(support.read(adminResult, ExpenseResponse.class).id());
		assertThat(countExpenses(client.id())).isEqualTo(2);
	}

	@Test
	void differentKeys_allowSeparateExpenses() throws Exception {
		postExpense(client.id(), "key-a-" + UUID.randomUUID(), expenseJson("1000.00", "Vendor A"), null)
				.andExpect(status().isCreated());
		postExpense(client.id(), "key-b-" + UUID.randomUUID(), expenseJson("1000.00", "Vendor A"), null)
				.andExpect(status().isCreated());
		assertThat(countExpenses(client.id())).isEqualTo(2);
	}

	@Test
	void differentKeys_sameBankLine_businessInvariantBlocksSecondExpense() throws Exception {
		CategoryResponse category = expenseCategory;
		BankAccountResponse account = createBankAccount(client.id());
		String csv = "Date,Description,Reference,Debit,Credit,Balance\n"
				+ "2026-09-20,OFFICE SUPPLIES,REF-1,250.00,,9000.00\n";
		MockMultipartFile file = new MockMultipartFile("file", "line.csv", "text/csv", csv.getBytes(StandardCharsets.UTF_8));
		mockMvc.perform(multipart("/api/v1/clients/" + client.id() + "/bank/imports")
						.file(file)
						.param("bankAccountId", account.id().toString())
						.header(HttpHeaders.AUTHORIZATION, IntegrationTestSupport.bearer(admin.token()))
						.header(IDEMPOTENCY, IntegrationTestSupport.newIdempotencyKey()))
				.andExpect(status().isCreated());
		UUID bankTxnId = listTransactions(client.id(), account.id()).get(0).id();
		String path = "/api/v1/clients/" + client.id() + "/bank/transactions/" + bankTxnId + "/create-expense";
		String body = "{\"categoryId\":\"" + category.id() + "\",\"vendorName\":\"Vendor\",\"description\":\"From bank\"}";
		mockMvc.perform(post(path)
						.header(HttpHeaders.AUTHORIZATION, IntegrationTestSupport.bearer(admin.token()))
						.header(IDEMPOTENCY, "bank-a-" + UUID.randomUUID())
						.contentType(MediaType.APPLICATION_JSON)
						.content(body))
				.andExpect(status().isCreated());
		MvcResult blocked = mockMvc.perform(post(path)
						.header(HttpHeaders.AUTHORIZATION, IntegrationTestSupport.bearer(admin.token()))
						.header(IDEMPOTENCY, "bank-b-" + UUID.randomUUID())
						.contentType(MediaType.APPLICATION_JSON)
						.content(body))
				.andExpect(status().isUnprocessableEntity())
				.andReturn();
		assertThat(errorCode(blocked)).isEqualTo("BANK_TRANSACTION_ALREADY_CONVERTED");
		assertThat(countExpenses(client.id())).isEqualTo(1);
	}

	@Test
	void queryString_changesRequestIdentity() throws Exception {
		String key = "query-" + UUID.randomUUID();
		String body = expenseJson("1000.00", "Vendor A");
		postExpense(client.id(), key, body, "trace=1").andExpect(status().isCreated());
		MvcResult conflict = postExpense(client.id(), key, body, null).andExpect(status().isConflict()).andReturn();
		assertThat(errorCode(conflict)).isEqualTo("IDEMPOTENCY_KEY_REUSED_WITH_DIFFERENT_REQUEST");
		assertThat(countExpenses(client.id())).isEqualTo(1);
	}

	@Test
	void whitespaceDifference_isDifferentRequest() throws Exception {
		String key = "ws-" + UUID.randomUUID();
		String compact = expenseJson("1000.00", "Vendor A");
		String spaced = compact.replace("\"amount\":1000.00", "\"amount\": 1000.00");
		postExpense(client.id(), key, compact, null).andExpect(status().isCreated());
		MvcResult conflict = postExpense(client.id(), key, spaced, null).andExpect(status().isConflict()).andReturn();
		assertThat(errorCode(conflict)).isEqualTo("IDEMPOTENCY_KEY_REUSED_WITH_DIFFERENT_REQUEST");
		assertThat(countExpenses(client.id())).isEqualTo(1);
	}

	@Test
	void validationFailure_isReplayed_withoutCreatingExpense() throws Exception {
		String key = "invalid-" + UUID.randomUUID();
		String body = "{\"amount\":1000.00}";
		MvcResult first = postExpense(client.id(), key, body, null).andExpect(status().isBadRequest()).andReturn();
		MvcResult second = postExpense(client.id(), key, body, null).andExpect(status().isBadRequest()).andReturn();
		assertThat(second.getResponse().getContentAsString()).isEqualTo(first.getResponse().getContentAsString());
		assertThat(countExpenses(client.id())).isZero();
	}

	@Test
	void missingKey_isRejected() throws Exception {
		MvcResult result = mockMvc.perform(post("/api/v1/clients/" + client.id() + "/expenses")
						.header(HttpHeaders.AUTHORIZATION, IntegrationTestSupport.bearer(admin.token()))
						.contentType(MediaType.APPLICATION_JSON)
						.content(expenseJson("1000.00", "Vendor A")))
				.andExpect(status().isBadRequest())
				.andReturn();
		assertThat(errorCode(result)).isEqualTo("IDEMPOTENCY_KEY_REQUIRED");
		assertThat(countExpenses(client.id())).isZero();
	}

	@Test
	void malformedKey_isRejected() throws Exception {
		MvcResult result = postExpense(client.id(), "bad key", expenseJson("1000.00", "Vendor A"), null)
				.andExpect(status().isBadRequest())
				.andReturn();
		assertThat(errorCode(result)).isEqualTo("VALIDATION_FAILED");
		assertThat(countExpenses(client.id())).isZero();
	}

	@Test
	void concurrentSameKey_singleDatabaseClaim() throws Exception {
		String key = "race-" + UUID.randomUUID();
		String path = "/api/v1/clients/" + client.id() + "/expenses";
		String body = expenseJson("1000.00", "Vendor A");
		String bodyHash = IdempotencyService.sha256(body.getBytes(StandardCharsets.UTF_8));
		String requestHash = IdempotencyService.sha256("POST " + path + " " + bodyHash);
		TenantContext context = new TenantContext(admin.userId(), admin.firmId(), UserRole.ADMIN, Set.of());
		int threads = 8;
		ExecutorService pool = Executors.newFixedThreadPool(threads);
		CountDownLatch ready = new CountDownLatch(threads);
		CountDownLatch start = new CountDownLatch(1);
		AtomicInteger proceeds = new AtomicInteger();
		AtomicInteger inProgress = new AtomicInteger();
		List<Future<?>> futures = new ArrayList<>();
		try {
			for (int i = 0; i < threads; i++) {
				futures.add(pool.submit(() -> {
					ready.countDown();
					start.await();
					TenantContextHolder.set(context);
					try {
						IdempotencyBeginResult result = idempotencyService.begin(key, "POST", path, requestHash);
						if (result.kind() == IdempotencyBeginResult.Kind.PROCEED) {
							proceeds.incrementAndGet();
						} else if (result.kind() == IdempotencyBeginResult.Kind.IN_PROGRESS) {
							inProgress.incrementAndGet();
						}
					} finally {
						TenantContextHolder.clear();
					}
					return null;
				}));
			}
			ready.await();
			start.countDown();
			for (Future<?> future : futures) {
				future.get();
			}
		} finally {
			pool.shutdownNow();
		}
		assertThat(proceeds.get()).isEqualTo(1);
		assertThat(inProgress.get()).isEqualTo(threads - 1);
		Integer claims = jdbcTemplate.queryForObject(
				"select count(*) from idempotency_keys where firm_id = ? and user_id = ? and status = 'STARTED'",
				Integer.class,
				admin.firmId(),
				admin.userId());
		assertThat(claims).isEqualTo(1);
	}

	@Test
	void concurrentHttpSameKey_createsOneExpense() throws Exception {
		String key = "http-race-" + UUID.randomUUID();
		String body = expenseJson("1000.00", "Vendor A");
		ExecutorService pool = Executors.newFixedThreadPool(2);
		CountDownLatch ready = new CountDownLatch(2);
		CountDownLatch start = new CountDownLatch(1);
		List<Callable<Integer>> tasks = new ArrayList<>();
		for (int i = 0; i < 2; i++) {
			tasks.add(() -> {
				ready.countDown();
				start.await();
				return postExpense(client.id(), key, body, null).andReturn().getResponse().getStatus();
			});
		}
		List<Future<Integer>> futures = new ArrayList<>();
		for (Callable<Integer> task : tasks) {
			futures.add(pool.submit(task));
		}
		ready.await();
		start.countDown();
		List<Integer> statuses = new ArrayList<>();
		for (Future<Integer> future : futures) {
			statuses.add(future.get());
		}
		pool.shutdownNow();
		assertThat(statuses).allMatch(status -> status == 201 || status == 409);
		assertThat(countExpenses(client.id())).isEqualTo(1);
	}

	@Test
	void serverError_releasesClaim_soSameKeyCanRetry() throws Exception {
		String key = "err-" + UUID.randomUUID();
		String path = "/api/v1/clients/" + client.id() + "/expenses";
		byte[] body = expenseJson("1000.00", "Vendor A").getBytes(StandardCharsets.UTF_8);
		TenantContextHolder.set(new TenantContext(admin.userId(), admin.firmId(), UserRole.ADMIN, Set.of()));
		try {
			MockHttpServletRequest request = new MockHttpServletRequest("POST", path);
			request.setRequestURI(path);
			request.setContentType(MediaType.APPLICATION_JSON_VALUE);
			request.setContent(body);
			request.addHeader(IDEMPOTENCY, key);
			MockHttpServletResponse response = new MockHttpServletResponse();
			idempotencyFilter.doFilter(request, response, (req, res) -> {
				HttpServletResponse http = (HttpServletResponse) res;
				http.setStatus(500);
				http.setContentType(MediaType.APPLICATION_JSON_VALUE);
				http.getOutputStream().write("{\"errorCode\":\"INTERNAL_ERROR\"}".getBytes(StandardCharsets.UTF_8));
			});
			assertThat(response.getStatus()).isEqualTo(500);
		} finally {
			TenantContextHolder.clear();
		}
		assertThat(countExpenses(client.id())).isZero();
		postExpense(client.id(), key, new String(body, StandardCharsets.UTF_8), null).andExpect(status().isCreated());
		assertThat(countExpenses(client.id())).isEqualTo(1);
	}

	@Test
	void staleProcessingClaim_isReclaimed() throws Exception {
		String key = "stale-" + UUID.randomUUID();
		String path = "/api/v1/clients/" + client.id() + "/expenses";
		String body = expenseJson("1000.00", "Vendor A");
		String bodyHash = IdempotencyService.sha256(body.getBytes(StandardCharsets.UTF_8));
		String requestHash = IdempotencyService.sha256("POST " + path + " " + bodyHash);
		TenantContextHolder.set(new TenantContext(admin.userId(), admin.firmId(), UserRole.ADMIN, Set.of()));
		try {
			IdempotencyBeginResult claim = idempotencyService.begin(key, "POST", path, requestHash);
			assertThat(claim.kind()).isEqualTo(IdempotencyBeginResult.Kind.PROCEED);
		} finally {
			TenantContextHolder.clear();
		}
		postExpense(client.id(), key, body, null).andExpect(status().isConflict());
		assertThat(countExpenses(client.id())).isZero();
		jdbcTemplate.update("""
				update idempotency_keys
				set created_at = now() - interval '5 minutes'
				where firm_id = ? and user_id = ? and path = ?
				""", admin.firmId(), admin.userId(), path);
		postExpense(client.id(), key, body, null).andExpect(status().isCreated());
		assertThat(countExpenses(client.id())).isEqualTo(1);
	}

	@Test
	void expiredCompletedKey_canBeReused() throws Exception {
		String key = "expire-" + UUID.randomUUID();
		String path = "/api/v1/clients/" + client.id() + "/expenses";
		String body = expenseJson("1000.00", "Vendor A");
		postExpense(client.id(), key, body, null).andExpect(status().isCreated());
		jdbcTemplate.update("""
				update idempotency_keys
				set created_at = now() - interval '25 hours'
				where firm_id = ? and user_id = ? and path = ?
				""", admin.firmId(), admin.userId(), path);
		postExpense(client.id(), key, body, null).andExpect(status().isCreated());
		assertThat(countExpenses(client.id())).isEqualTo(2);
	}

	private org.springframework.test.web.servlet.ResultActions postExpense(UUID clientId, String key, String body, String query)
			throws Exception {
		return postExpense(admin, clientId, key, body, query);
	}

	private org.springframework.test.web.servlet.ResultActions postExpense(
			IntegrationTestSupport.Session session,
			UUID clientId,
			String key,
			String body
	) throws Exception {
		return postExpense(session, clientId, key, body, null);
	}

	private org.springframework.test.web.servlet.ResultActions postExpense(
			IntegrationTestSupport.Session session,
			UUID clientId,
			String key,
			String body,
			String query
	) throws Exception {
		String url = "/api/v1/clients/" + clientId + "/expenses";
		if (query != null) {
			url = url + "?" + query;
		}
		return mockMvc.perform(post(url)
				.header(HttpHeaders.AUTHORIZATION, IntegrationTestSupport.bearer(session.token()))
				.header(IDEMPOTENCY, key)
				.contentType(MediaType.APPLICATION_JSON)
				.content(body));
	}

	private String expenseJson(String amount, String vendor) {
		return expenseJsonFor(expenseCategory.id(), amount, vendor);
	}

	private static String expenseJsonFor(UUID categoryId, String amount, String vendor) {
		return """
				{"transactionDate":"%s","categoryId":"%s","amount":%s,"currencyCode":"LKR","vendorName":"%s"}
				""".formatted(LocalDate.of(2026, 9, 20), categoryId, amount, vendor).trim();
	}

	private String incomeJson(String amount) {
		return """
				{"transactionDate":"2026-09-20","categoryId":"%s","amount":%s,"currencyCode":"LKR","customerName":"Customer","paymentMethod":"BANK_TRANSFER"}
				""".formatted(incomeCategory.id(), amount);
	}

	private String errorCode(MvcResult result) throws Exception {
		JsonNode problem = objectMapper.readTree(result.getResponse().getContentAsString());
		return problem.get("errorCode").asText();
	}

	private long countExpenses(UUID clientId) {
		Long count = jdbcTemplate.queryForObject("select count(*) from expenses where client_id = ?", Long.class, clientId);
		return count == null ? 0 : count;
	}

	private long countIncome(UUID clientId) {
		Long count = jdbcTemplate.queryForObject("select count(*) from income where client_id = ?", Long.class, clientId);
		return count == null ? 0 : count;
	}

	private ClientResponse createClient(IntegrationTestSupport.Session session, String name) throws Exception {
		MvcResult result = mockMvc.perform(post("/api/v1/clients")
						.header(HttpHeaders.AUTHORIZATION, IntegrationTestSupport.bearer(session.token()))
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"name\":\"" + name + "\"}"))
				.andExpect(status().isCreated())
				.andReturn();
		return support.read(result, ClientResponse.class);
	}

	private CategoryResponse createCategory(IntegrationTestSupport.Session session, String code, String name, String type)
			throws Exception {
		MvcResult result = mockMvc.perform(post("/api/v1/categories")
						.header(HttpHeaders.AUTHORIZATION, IntegrationTestSupport.bearer(session.token()))
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"code\":\"" + code + "\",\"name\":\"" + name + "\",\"categoryType\":\"" + type + "\"}"))
				.andExpect(status().isCreated())
				.andReturn();
		return support.read(result, CategoryResponse.class);
	}

	private BankAccountResponse createBankAccount(UUID clientId) throws Exception {
		MvcResult result = mockMvc.perform(post("/api/v1/clients/" + clientId + "/bank/accounts")
						.header(HttpHeaders.AUTHORIZATION, IntegrationTestSupport.bearer(admin.token()))
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{
								  "bankName": "Idem Bank",
								  "accountName": "Operating",
								  "maskedAccountNumber": "****3333",
								  "currency": "LKR"
								}
								"""))
				.andExpect(status().isCreated())
				.andReturn();
		return support.read(result, BankAccountResponse.class);
	}

	private List<BankTransactionResponse> listTransactions(UUID clientId, UUID bankAccountId) throws Exception {
		MvcResult result = mockMvc.perform(get("/api/v1/clients/" + clientId + "/bank/transactions")
						.param("bankAccountId", bankAccountId.toString())
						.param("size", "20")
						.header(HttpHeaders.AUTHORIZATION, IntegrationTestSupport.bearer(admin.token())))
				.andExpect(status().isOk())
				.andReturn();
		JsonNode page = objectMapper.readTree(result.getResponse().getContentAsString());
		List<BankTransactionResponse> rows = new ArrayList<>();
		for (JsonNode node : page.get("content")) {
			rows.add(objectMapper.treeToValue(node, BankTransactionResponse.class));
		}
		return rows;
	}

	private static String suffix() {
		return UUID.randomUUID().toString().substring(0, 6);
	}
}
