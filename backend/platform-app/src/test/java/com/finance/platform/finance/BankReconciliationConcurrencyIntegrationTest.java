package com.finance.platform.finance;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.finance.platform.finance.application.dto.BankAccountResponse;
import com.finance.platform.finance.application.dto.BankTransactionResponse;
import com.finance.platform.finance.application.dto.CategoryResponse;
import com.finance.platform.finance.application.dto.ClientResponse;
import com.finance.platform.finance.application.dto.CreateExpenseRequest;
import com.finance.platform.finance.application.dto.ExpenseResponse;
import com.finance.platform.finance.application.dto.IncomeRequest;
import com.finance.platform.finance.application.dto.IncomeResponse;
import com.finance.platform.finance.domain.model.PaymentMethod;
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

import java.math.BigDecimal;
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
 * Slice 6 — reconciliation concurrency and durable matching invariants (PostgreSQL / Testcontainers).
 */
class BankReconciliationConcurrencyIntegrationTest extends AbstractPostgresIntegrationTest {

	private static final String IDEMPOTENCY = "Idempotency-Key";

	@Autowired
	private ObjectMapper objectMapper;

	@Autowired
	private JdbcTemplate jdbcTemplate;

	private IntegrationTestSupport.Session admin;

	@BeforeEach
	void setUp() throws Exception {
		support.seedRolesIfNeeded();
		admin = support.registerFirmAdmin("Recon Concurrency Firm");
	}

	@Test
	void confirmMatch_once_succeeds() throws Exception {
		DebitScenario scenario = seedDebitLine("400.00");
		ExpenseResponse expense = approveExpense(scenario, createApprovedExpense(scenario, "400.00", "Vendor A"));
		confirmExpenseMatch(scenario, expense.id(), IntegrationTestSupport.newIdempotencyKey());
		assertThat(countConfirmedMatches(scenario.bankTransactionId())).isEqualTo(1);
		assertThat(bankStatus(scenario.bankTransactionId())).isEqualTo("MATCHED");
	}

	@Test
	void sequentialDuplicate_sameBankSecondExpense_rejected() throws Exception {
		DebitScenario scenario = seedDebitLine("500.00");
		ExpenseResponse first = approveExpense(scenario, createApprovedExpense(scenario, "500.00", "Vendor One"));
		ExpenseResponse second = approveExpense(scenario, createApprovedExpense(scenario, "500.00", "Vendor Two"));
		confirmExpenseMatch(scenario, first.id(), IntegrationTestSupport.newIdempotencyKey());
		MvcResult conflict = mockMvc.perform(post(confirmPath(scenario))
						.header(HttpHeaders.AUTHORIZATION, IntegrationTestSupport.bearer(admin.token()))
						.header(IDEMPOTENCY, IntegrationTestSupport.newIdempotencyKey())
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"expenseId\":\"" + second.id() + "\"}"))
				.andExpect(status().isUnprocessableEntity())
				.andReturn();
		JsonNode problem = objectMapper.readTree(conflict.getResponse().getContentAsString());
		assertThat(problem.get("errorCode").asText()).isEqualTo("BANK_TRANSACTION_ALREADY_MATCHED");
		assertThat(countConfirmedMatches(scenario.bankTransactionId())).isEqualTo(1);
	}

	@Test
	void concurrentSameBankDifferentExpenses_oneConfirmedMatch() throws Exception {
		DebitScenario scenario = seedDebitLine("600.00");
		ExpenseResponse expenseA = approveExpense(scenario, createApprovedExpense(scenario, "600.00", "Race A"));
		ExpenseResponse expenseB = approveExpense(scenario, createApprovedExpense(scenario, "600.00", "Race B"));
		List<Integer> statuses = runConcurrentConfirm(scenario, expenseA.id(), expenseB.id());
		assertThat(statuses.stream().filter(s -> s == 200).count()).isEqualTo(1);
		assertThat(statuses.stream().filter(s -> s == 422).count()).isEqualTo(1);
		assertThat(countConfirmedMatches(scenario.bankTransactionId())).isEqualTo(1);
	}

	@Test
	void concurrentDifferentBanksSameExpense_oneConfirmedMatch() throws Exception {
		DebitScenario bankA = seedDebitLine("700.00");
		importDebitLine(bankA.clientId(), bankA.bankAccountId(), "700.00", "REF-B");
		List<BankTransactionResponse> txns = listTransactionsFor(admin, bankA.clientId(), bankA.bankAccountId());
		assertThat(txns).hasSize(2);
		UUID bankBId = txns.stream().filter(t -> !t.id().equals(bankA.bankTransactionId())).findFirst().orElseThrow().id();
		ExpenseResponse shared = approveExpense(bankA, createApprovedExpense(bankA, "700.00", "Shared Vendor"));
		List<Integer> statuses = runConcurrentConfirmTwoBanks(bankA, bankA.bankTransactionId(), bankBId, shared.id());
		assertThat(statuses.stream().filter(s -> s == 200).count()).isEqualTo(1);
		assertThat(statuses.stream().filter(s -> s == 422).count()).isEqualTo(1);
		assertThat(countConfirmedMatchesForExpense(shared.id())).isEqualTo(1);
	}

	@Test
	void sameIdempotencyKey_replaysConfirm() throws Exception {
		DebitScenario scenario = seedDebitLine("310.00");
		ExpenseResponse expense = approveExpense(scenario, createApprovedExpense(scenario, "310.00", "Replay Vendor"));
		String key = IntegrationTestSupport.newIdempotencyKey();
		confirmExpenseMatch(scenario, expense.id(), key);
		mockMvc.perform(post(confirmPath(scenario))
						.header(HttpHeaders.AUTHORIZATION, IntegrationTestSupport.bearer(admin.token()))
						.header(IDEMPOTENCY, key)
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"expenseId\":\"" + expense.id() + "\"}"))
				.andExpect(status().isOk());
		assertThat(countConfirmedMatches(scenario.bankTransactionId())).isEqualTo(1);
	}

	@Test
	void differentIdempotencyKeys_sameBankDifferentExpenses_dbInvariant() throws Exception {
		DebitScenario scenario = seedDebitLine("820.00");
		ExpenseResponse first = approveExpense(scenario, createApprovedExpense(scenario, "820.00", "Key One"));
		ExpenseResponse second = approveExpense(scenario, createApprovedExpense(scenario, "820.00", "Key Two"));
		confirmExpenseMatch(scenario, first.id(), IntegrationTestSupport.newIdempotencyKey());
		mockMvc.perform(post(confirmPath(scenario))
						.header(HttpHeaders.AUTHORIZATION, IntegrationTestSupport.bearer(admin.token()))
						.header(IDEMPOTENCY, IntegrationTestSupport.newIdempotencyKey())
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"expenseId\":\"" + second.id() + "\"}"))
				.andExpect(status().isUnprocessableEntity());
		assertThat(countConfirmedMatches(scenario.bankTransactionId())).isEqualTo(1);
	}

	@Test
	void unmatch_thenRematch_differentExpense() throws Exception {
		DebitScenario scenario = seedDebitLine("900.00");
		ExpenseResponse first = approveExpense(scenario, createApprovedExpense(scenario, "900.00", "First"));
		ExpenseResponse second = approveExpense(scenario, createApprovedExpense(scenario, "900.00", "Second"));
		confirmExpenseMatch(scenario, first.id(), IntegrationTestSupport.newIdempotencyKey());
		mockMvc.perform(post("/api/v1/clients/" + scenario.clientId() + "/bank/transactions/"
						+ scenario.bankTransactionId() + "/unmatch")
						.header(HttpHeaders.AUTHORIZATION, IntegrationTestSupport.bearer(admin.token())))
				.andExpect(status().isOk());
		assertThat(bankStatus(scenario.bankTransactionId())).isIn("UNMATCHED", "SUGGESTED");
		confirmExpenseMatch(scenario, second.id(), IntegrationTestSupport.newIdempotencyKey());
		assertThat(countConfirmedMatches(scenario.bankTransactionId())).isEqualTo(1);
		Long activeForFirst = jdbcTemplate.queryForObject("""
				select count(*) from reconciliation_matches
				where expense_id = ? and status = 'CONFIRMED'
				""", Long.class, first.id());
		assertThat(activeForFirst).isZero();
	}

	@Test
	void concurrentReconcileAndUnmatch_consistentFinalState() throws Exception {
		DebitScenario scenario = seedDebitLine("440.00");
		ExpenseResponse expense = approveExpense(scenario, createApprovedExpense(scenario, "440.00", "Unmatch Race"));
		ExecutorService pool = Executors.newFixedThreadPool(2);
		CountDownLatch ready = new CountDownLatch(2);
		CountDownLatch start = new CountDownLatch(1);
		Callable<Integer> confirm = () -> {
			ready.countDown();
			start.await();
			return mockMvc.perform(post(confirmPath(scenario))
							.header(HttpHeaders.AUTHORIZATION, IntegrationTestSupport.bearer(admin.token()))
							.header(IDEMPOTENCY, IntegrationTestSupport.newIdempotencyKey())
							.contentType(MediaType.APPLICATION_JSON)
							.content("{\"expenseId\":\"" + expense.id() + "\"}"))
					.andReturn().getResponse().getStatus();
		};
		Callable<Integer> unmatch = () -> {
			ready.countDown();
			start.await();
			return mockMvc.perform(post("/api/v1/clients/" + scenario.clientId() + "/bank/transactions/"
							+ scenario.bankTransactionId() + "/unmatch")
							.header(HttpHeaders.AUTHORIZATION, IntegrationTestSupport.bearer(admin.token())))
					.andReturn().getResponse().getStatus();
		};
		Future<Integer> confirmFuture = pool.submit(confirm);
		Future<Integer> unmatchFuture = pool.submit(unmatch);
		ready.await();
		start.countDown();
		confirmFuture.get();
		unmatchFuture.get();
		pool.shutdownNow();
		String status = bankStatus(scenario.bankTransactionId());
		long confirmed = countConfirmedMatches(scenario.bankTransactionId());
		assertThat(status.equals("MATCHED") || status.equals("UNMATCHED")).isTrue();
		if ("MATCHED".equals(status)) {
			assertThat(confirmed).isEqualTo(1);
		} else {
			assertThat(confirmed).isZero();
		}
	}

	@Test
	void createFromBank_racesConfirmExisting_oneOutcome() throws Exception {
		DebitScenario scenario = seedDebitLine("275.00");
		ExpenseResponse existing = approveExpense(scenario, createApprovedExpense(scenario, "275.00", "Existing"));
		ExecutorService pool = Executors.newFixedThreadPool(2);
		CountDownLatch ready = new CountDownLatch(2);
		CountDownLatch start = new CountDownLatch(1);
		Callable<Integer> createFromBank = () -> {
			ready.countDown();
			start.await();
			return mockMvc.perform(post("/api/v1/clients/" + scenario.clientId() + "/bank/transactions/"
							+ scenario.bankTransactionId() + "/create-expense")
							.header(HttpHeaders.AUTHORIZATION, IntegrationTestSupport.bearer(admin.token()))
							.header(IDEMPOTENCY, IntegrationTestSupport.newIdempotencyKey())
							.contentType(MediaType.APPLICATION_JSON)
							.content("{\"categoryId\":\"" + scenario.categoryId() + "\"}"))
					.andReturn().getResponse().getStatus();
		};
		Callable<Integer> confirm = () -> {
			ready.countDown();
			start.await();
			return mockMvc.perform(post(confirmPath(scenario))
							.header(HttpHeaders.AUTHORIZATION, IntegrationTestSupport.bearer(admin.token()))
							.header(IDEMPOTENCY, IntegrationTestSupport.newIdempotencyKey())
							.contentType(MediaType.APPLICATION_JSON)
							.content("{\"expenseId\":\"" + existing.id() + "\"}"))
					.andReturn().getResponse().getStatus();
		};
		Future<Integer> createFuture = pool.submit(createFromBank);
		Future<Integer> confirmFuture = pool.submit(confirm);
		ready.await();
		start.countDown();
		int createStatus = createFuture.get();
		int confirmStatus = confirmFuture.get();
		pool.shutdownNow();
		int successes = (createStatus == 201 ? 1 : 0) + (confirmStatus == 200 ? 1 : 0);
		assertThat(successes).isEqualTo(1);
		long confirmed = countConfirmedMatches(scenario.bankTransactionId());
		Long generations = jdbcTemplate.queryForObject(
				"select count(*) from bank_transaction_ledger_generations where bank_transaction_id = ?",
				Long.class, scenario.bankTransactionId());
		if (confirmStatus == 200) {
			assertThat(confirmed).isEqualTo(1);
			assertThat(generations).isZero();
		} else {
			assertThat(generations).isEqualTo(1);
			assertThat(confirmed).isZero();
		}
	}

	@Test
	void crossType_creditLine_incomeVsInvoicePayment_oneConsumesBank() throws Exception {
		CreditScenario scenario = seedCreditLine("1000.00");
		IncomeResponse income = approveIncome(scenario, createApprovedIncome(scenario, "1000.00", "Payer"));
		UUID customerId = createArCustomer();
		ExecutorService pool = Executors.newFixedThreadPool(2);
		CountDownLatch ready = new CountDownLatch(2);
		CountDownLatch start = new CountDownLatch(1);
		Callable<Integer> confirmIncome = () -> {
			ready.countDown();
			start.await();
			return mockMvc.perform(post(confirmPath(scenario))
							.header(HttpHeaders.AUTHORIZATION, IntegrationTestSupport.bearer(admin.token()))
							.header(IDEMPOTENCY, IntegrationTestSupport.newIdempotencyKey())
							.contentType(MediaType.APPLICATION_JSON)
							.content("{\"incomeId\":\"" + income.id() + "\"}"))
					.andReturn().getResponse().getStatus();
		};
		Callable<Integer> confirmInvoice = () -> {
			ready.countDown();
			start.await();
			return mockMvc.perform(post("/api/v1/clients/" + scenario.clientId() + "/bank/transactions/"
							+ scenario.bankTransactionId() + "/confirm-invoice-payment")
							.header(HttpHeaders.AUTHORIZATION, IntegrationTestSupport.bearer(admin.token()))
							.contentType(MediaType.APPLICATION_JSON)
							.content("{\"customerId\":\"" + customerId + "\",\"allocations\":[]}"))
					.andReturn().getResponse().getStatus();
		};
		Future<Integer> incomeFuture = pool.submit(confirmIncome);
		Future<Integer> invoiceFuture = pool.submit(confirmInvoice);
		ready.await();
		start.countDown();
		incomeFuture.get();
		invoiceFuture.get();
		pool.shutdownNow();
		assertThat(bankStatus(scenario.bankTransactionId())).isEqualTo("MATCHED");
		long reconMatches = countConfirmedMatches(scenario.bankTransactionId());
		Long arPayments = jdbcTemplate.queryForObject(
				"select count(*) from ar_payments where bank_transaction_id = ? and status <> 'REVERSED'",
				Long.class, scenario.bankTransactionId());
		assertThat(reconMatches + arPayments).isEqualTo(1);
	}

	@Test
	void crossClientBankTransactionConfirm_notFound() throws Exception {
		IntegrationTestSupport.Session other = support.registerFirmAdmin("Other Recon Firm");
		DebitScenario victim = seedDebitLineFor(other, "150.00");
		ClientResponse attackerClient = createClientFor(admin, "Attacker");
		CategoryResponse category = createCategory("RC-ATT", "Attacker", "EXPENSE");
		DebitScenario attacker = new DebitScenario(attackerClient.id(), victim.bankTransactionId(), victim.bankAccountId(), category.id());
		ExpenseResponse expense = approveExpense(attacker, createApprovedExpense(attacker, "150.00", "Wrong"));
		mockMvc.perform(post("/api/v1/clients/" + attackerClient.id() + "/bank/transactions/"
						+ victim.bankTransactionId() + "/confirm")
						.header(HttpHeaders.AUTHORIZATION, IntegrationTestSupport.bearer(admin.token()))
						.header(IDEMPOTENCY, IntegrationTestSupport.newIdempotencyKey())
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"expenseId\":\"" + expense.id() + "\"}"))
				.andExpect(status().isNotFound());
	}

	@Test
	void reconciliationConfirmedAudit_writtenOnce() throws Exception {
		DebitScenario scenario = seedDebitLine("360.00");
		ExpenseResponse expense = approveExpense(scenario, createApprovedExpense(scenario, "360.00", "Audit Vendor"));
		confirmExpenseMatch(scenario, expense.id(), IntegrationTestSupport.newIdempotencyKey());
		mockMvc.perform(post(confirmPath(scenario))
						.header(HttpHeaders.AUTHORIZATION, IntegrationTestSupport.bearer(admin.token()))
						.header(IDEMPOTENCY, IntegrationTestSupport.newIdempotencyKey())
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"expenseId\":\"" + expense.id() + "\"}"))
				.andExpect(status().isUnprocessableEntity());
		Integer audits = jdbcTemplate.queryForObject("""
				select count(*) from audit_log
				where action = 'RECONCILIATION_CONFIRMED' and resource_id = ?
				""", Integer.class, scenario.bankTransactionId());
		assertThat(audits).isEqualTo(1);
	}

	private record DebitScenario(UUID clientId, UUID bankTransactionId, UUID bankAccountId, UUID categoryId) {
	}

	private record CreditScenario(UUID clientId, UUID bankTransactionId, UUID bankAccountId, UUID categoryId) {
	}

	private List<Integer> runConcurrentConfirm(DebitScenario scenario, UUID expenseA, UUID expenseB) throws Exception {
		ExecutorService pool = Executors.newFixedThreadPool(2);
		CountDownLatch ready = new CountDownLatch(2);
		CountDownLatch start = new CountDownLatch(1);
		List<Callable<Integer>> tasks = List.of(
				() -> invokeConfirm(scenario, expenseA, ready, start),
				() -> invokeConfirm(scenario, expenseB, ready, start));
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
		return statuses;
	}

	private List<Integer> runConcurrentConfirmTwoBanks(
			DebitScenario scenario,
			UUID bankA,
			UUID bankB,
			UUID expenseId
	) throws Exception {
		ExecutorService pool = Executors.newFixedThreadPool(2);
		CountDownLatch ready = new CountDownLatch(2);
		CountDownLatch start = new CountDownLatch(1);
		Callable<Integer> taskA = () -> {
			ready.countDown();
			start.await();
			return mockMvc.perform(post("/api/v1/clients/" + scenario.clientId() + "/bank/transactions/" + bankA + "/confirm")
							.header(HttpHeaders.AUTHORIZATION, IntegrationTestSupport.bearer(admin.token()))
							.header(IDEMPOTENCY, IntegrationTestSupport.newIdempotencyKey())
							.contentType(MediaType.APPLICATION_JSON)
							.content("{\"expenseId\":\"" + expenseId + "\"}"))
					.andReturn().getResponse().getStatus();
		};
		Callable<Integer> taskB = () -> {
			ready.countDown();
			start.await();
			return mockMvc.perform(post("/api/v1/clients/" + scenario.clientId() + "/bank/transactions/" + bankB + "/confirm")
							.header(HttpHeaders.AUTHORIZATION, IntegrationTestSupport.bearer(admin.token()))
							.header(IDEMPOTENCY, IntegrationTestSupport.newIdempotencyKey())
							.contentType(MediaType.APPLICATION_JSON)
							.content("{\"expenseId\":\"" + expenseId + "\"}"))
					.andReturn().getResponse().getStatus();
		};
		Future<Integer> futureA = pool.submit(taskA);
		Future<Integer> futureB = pool.submit(taskB);
		ready.await();
		start.countDown();
		List<Integer> statuses = List.of(futureA.get(), futureB.get());
		pool.shutdownNow();
		return statuses;
	}

	private int invokeConfirm(DebitScenario scenario, UUID expenseId, CountDownLatch ready, CountDownLatch start)
			throws Exception {
		ready.countDown();
		start.await();
		return mockMvc.perform(post(confirmPath(scenario))
						.header(HttpHeaders.AUTHORIZATION, IntegrationTestSupport.bearer(admin.token()))
						.header(IDEMPOTENCY, IntegrationTestSupport.newIdempotencyKey())
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"expenseId\":\"" + expenseId + "\"}"))
				.andReturn().getResponse().getStatus();
	}

	private void confirmExpenseMatch(DebitScenario scenario, UUID expenseId, String idempotencyKey) throws Exception {
		mockMvc.perform(post(confirmPath(scenario))
						.header(HttpHeaders.AUTHORIZATION, IntegrationTestSupport.bearer(admin.token()))
						.header(IDEMPOTENCY, idempotencyKey)
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"expenseId\":\"" + expenseId + "\"}"))
				.andExpect(status().isOk());
	}

	private static String confirmPath(DebitScenario scenario) {
		return "/api/v1/clients/" + scenario.clientId() + "/bank/transactions/" + scenario.bankTransactionId() + "/confirm";
	}

	private static String confirmPath(CreditScenario scenario) {
		return "/api/v1/clients/" + scenario.clientId() + "/bank/transactions/" + scenario.bankTransactionId() + "/confirm";
	}

	private long countConfirmedMatches(UUID bankTransactionId) {
		Long count = jdbcTemplate.queryForObject("""
				select count(*) from reconciliation_matches
				where bank_transaction_id = ? and status = 'CONFIRMED'
				""", Long.class, bankTransactionId);
		return count == null ? 0 : count;
	}

	private long countConfirmedMatchesForExpense(UUID expenseId) {
		Long count = jdbcTemplate.queryForObject("""
				select count(*) from reconciliation_matches
				where expense_id = ? and status = 'CONFIRMED'
				""", Long.class, expenseId);
		return count == null ? 0 : count;
	}

	private String bankStatus(UUID bankTransactionId) {
		return jdbcTemplate.queryForObject(
				"select match_status from bank_transactions where id = ?",
				String.class,
				bankTransactionId);
	}

	private DebitScenario seedDebitLine(String amount) throws Exception {
		return seedDebitLineFor(admin, amount);
	}

	private DebitScenario seedDebitLineFor(IntegrationTestSupport.Session session, String amount) throws Exception {
		ClientResponse client = createClientFor(session, "Client " + UUID.randomUUID().toString().substring(0, 6));
		CategoryResponse category = createCategoryFor(session, "RC-" + UUID.randomUUID().toString().substring(0, 6), "Ops", "EXPENSE");
		BankAccountResponse account = createBankAccountFor(session, client.id());
		importDebitLineFor(session, client.id(), account.id(), amount, "REF-1");
		UUID bankTxnId = listTransactionsFor(session, client.id(), account.id()).get(0).id();
		return new DebitScenario(client.id(), bankTxnId, account.id(), category.id());
	}

	private CreditScenario seedCreditLine(String amount) throws Exception {
		java.time.LocalDate txnDate = java.time.LocalDate.now().withDayOfMonth(Math.min(22, java.time.LocalDate.now().lengthOfMonth()));
		ClientResponse client = createClientFor(admin, "Credit Client");
		CategoryResponse category = createCategoryFor(admin, "RC-IN", "Sales", "INCOME");
		BankAccountResponse account = createBankAccountFor(admin, client.id());
		String csv = "Date,Description,Reference,Debit,Credit,Balance\n"
				+ txnDate + ",PAYMENT,,," + amount + ",11000.00\n";
		importCsvFor(admin, client.id(), account.id(), "credit.csv", csv);
		UUID bankTxnId = listTransactionsFor(admin, client.id(), account.id()).get(0).id();
		return new CreditScenario(client.id(), bankTxnId, account.id(), category.id());
	}

	private void importDebitLine(UUID clientId, UUID bankAccountId, String amount, String ref) throws Exception {
		importDebitLineFor(admin, clientId, bankAccountId, amount, ref);
	}

	private void importDebitLineFor(
			IntegrationTestSupport.Session session,
			UUID clientId,
			UUID bankAccountId,
			String amount,
			String ref
	) throws Exception {
		java.time.LocalDate txnDate = java.time.LocalDate.now().withDayOfMonth(Math.min(21, java.time.LocalDate.now().lengthOfMonth()));
		String csv = "Date,Description,Reference,Debit,Credit,Balance\n"
				+ txnDate + ",VENDOR," + ref + "," + amount + ",,9000.00\n";
		importCsvFor(session, clientId, bankAccountId, "line.csv", csv);
	}

	private ExpenseResponse createApprovedExpense(DebitScenario scenario, String amount, String vendor) throws Exception {
		java.time.LocalDate txnDate = java.time.LocalDate.now().withDayOfMonth(Math.min(21, java.time.LocalDate.now().lengthOfMonth()));
		MvcResult result = mockMvc.perform(post("/api/v1/clients/" + scenario.clientId() + "/expenses")
						.header(HttpHeaders.AUTHORIZATION, IntegrationTestSupport.bearer(admin.token()))
						.header(IDEMPOTENCY, IntegrationTestSupport.newIdempotencyKey())
						.contentType(MediaType.APPLICATION_JSON)
						.content(objectMapper.writeValueAsString(new CreateExpenseRequest(
								txnDate,
								scenario.categoryId(),
								new BigDecimal(amount),
								"LKR",
								vendor,
								null,
								null,
								null))))
				.andExpect(status().isCreated())
				.andReturn();
		return support.read(result, ExpenseResponse.class);
	}

	private IncomeResponse createApprovedIncome(CreditScenario scenario, String amount, String payer) throws Exception {
		java.time.LocalDate txnDate = java.time.LocalDate.now().withDayOfMonth(Math.min(22, java.time.LocalDate.now().lengthOfMonth()));
		MvcResult result = mockMvc.perform(post("/api/v1/clients/" + scenario.clientId() + "/income")
						.header(HttpHeaders.AUTHORIZATION, IntegrationTestSupport.bearer(admin.token()))
						.header(IDEMPOTENCY, IntegrationTestSupport.newIdempotencyKey())
						.contentType(MediaType.APPLICATION_JSON)
						.content(objectMapper.writeValueAsString(new IncomeRequest(
								txnDate,
								scenario.categoryId(),
								new BigDecimal(amount),
								"LKR",
								payer,
								null,
								PaymentMethod.BANK_TRANSFER,
								null,
								null))))
				.andExpect(status().isCreated())
				.andReturn();
		return support.read(result, IncomeResponse.class);
	}

	private ExpenseResponse approveExpense(DebitScenario scenario, ExpenseResponse draft) throws Exception {
		MvcResult result = mockMvc.perform(post("/api/v1/clients/" + scenario.clientId() + "/expenses/" + draft.id() + "/approve")
						.header(HttpHeaders.AUTHORIZATION, IntegrationTestSupport.bearer(admin.token()))
						.header(IDEMPOTENCY, IntegrationTestSupport.newIdempotencyKey()))
				.andExpect(status().isOk())
				.andReturn();
		return support.read(result, ExpenseResponse.class);
	}

	private IncomeResponse approveIncome(CreditScenario scenario, IncomeResponse draft) throws Exception {
		MvcResult result = mockMvc.perform(post("/api/v1/clients/" + scenario.clientId() + "/income/" + draft.id() + "/approve")
						.header(HttpHeaders.AUTHORIZATION, IntegrationTestSupport.bearer(admin.token()))
						.header(IDEMPOTENCY, IntegrationTestSupport.newIdempotencyKey()))
				.andExpect(status().isOk())
				.andReturn();
		return support.read(result, IncomeResponse.class);
	}

	private UUID createArCustomer() throws Exception {
		MvcResult result = mockMvc.perform(post("/api/v1/ar/customers")
						.header(HttpHeaders.AUTHORIZATION, IntegrationTestSupport.bearer(admin.token()))
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"name\":\"Recon Customer\",\"email\":\"recon@test.com\",\"paymentTermsDays\":30}"))
				.andExpect(status().isCreated())
				.andReturn();
		return UUID.fromString(objectMapper.readTree(result.getResponse().getContentAsString()).get("id").asText());
	}

	private ClientResponse createClient(String name) throws Exception {
		return createClientFor(admin, name);
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
								  "bankName": "Recon Bank",
								  "accountName": "Operating",
								  "maskedAccountNumber": "****3333",
								  "currency": "LKR"
								}
								"""))
				.andExpect(status().isCreated())
				.andReturn();
		return support.read(result, BankAccountResponse.class);
	}

	private void importCsv(UUID clientId, UUID bankAccountId, String fileName, String csv) throws Exception {
		importCsvFor(admin, clientId, bankAccountId, fileName, csv);
	}

	private void importCsvFor(
			IntegrationTestSupport.Session session,
			UUID clientId,
			UUID bankAccountId,
			String fileName,
			String csv
	) throws Exception {
		MockMultipartFile file = new MockMultipartFile("file", fileName, "text/csv", csv.getBytes(StandardCharsets.UTF_8));
		mockMvc.perform(multipart("/api/v1/clients/" + clientId + "/bank/imports")
						.file(file)
						.param("bankAccountId", bankAccountId.toString())
						.header(HttpHeaders.AUTHORIZATION, IntegrationTestSupport.bearer(session.token()))
						.header(IDEMPOTENCY, IntegrationTestSupport.newIdempotencyKey()))
				.andExpect(status().isCreated());
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
		return parseTransactionPage(result);
	}

	private List<BankTransactionResponse> listTransactions(
			IntegrationTestSupport.Session session,
			UUID clientId,
			UUID bankAccountId
	) throws Exception {
		return listTransactionsFor(session, clientId, bankAccountId);
	}

	private List<BankTransactionResponse> parseTransactionPage(MvcResult result) throws Exception {
		JsonNode page = objectMapper.readTree(result.getResponse().getContentAsString());
		List<BankTransactionResponse> rows = new ArrayList<>();
		for (JsonNode node : page.get("content")) {
			rows.add(objectMapper.treeToValue(node, BankTransactionResponse.class));
		}
		return rows;
	}
}
