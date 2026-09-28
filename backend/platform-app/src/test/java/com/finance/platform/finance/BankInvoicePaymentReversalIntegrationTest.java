package com.finance.platform.finance;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.finance.platform.finance.application.dto.BankAccountResponse;
import com.finance.platform.finance.application.dto.BankTransactionResponse;
import com.finance.platform.finance.application.dto.ClientResponse;
import com.finance.platform.finance.application.dto.PeriodResponse;
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
import java.time.LocalDate;
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
 * Slice 7 — invoice payment reversal, unmatch, and rematch lifecycle (PostgreSQL / Testcontainers).
 */
class BankInvoicePaymentReversalIntegrationTest extends AbstractPostgresIntegrationTest {

	private static final String IDEMPOTENCY = "Idempotency-Key";

	@Autowired
	private ObjectMapper objectMapper;

	@Autowired
	private JdbcTemplate jdbcTemplate;

	private IntegrationTestSupport.Session admin;

	@BeforeEach
	void setUp() throws Exception {
		support.seedRolesIfNeeded();
		admin = support.registerFirmAdmin("Invoice Reversal Firm");
	}

	@Test
	void confirmUnmatch_restoresOutstandingAndReleasesBank() throws Exception {
		CreditScenario scenario = seedCreditLine("500.00");
		UUID customerId = createCustomer();
		UUID invoiceId = createAndIssueInvoice(customerId, BigDecimal.valueOf(500));
		confirmInvoicePayment(scenario, customerId, invoiceId, "500");
		assertThat(bankStatus(scenario.bankTransactionId())).isEqualTo("MATCHED");
		JsonNode paid = getInvoice(invoiceId);
		assertThat(paid.get("settlementStatus").asText()).isEqualTo("PAID");

		unmatch(scenario);
		assertThat(bankStatus(scenario.bankTransactionId())).isIn("UNMATCHED", "SUGGESTED");
		JsonNode after = getInvoice(invoiceId);
		assertThat(after.get("settlementStatus").asText()).isEqualTo("UNPAID");
		assertThat(after.get("outstanding").decimalValue()).isEqualByComparingTo(BigDecimal.valueOf(500));

		Long activePayments = jdbcTemplate.queryForObject(
				"select count(*) from ar_payments where bank_transaction_id = ? and status <> 'REVERSED'",
				Long.class, scenario.bankTransactionId());
		assertThat(activePayments).isZero();
		Long reversedPayments = jdbcTemplate.queryForObject(
				"select count(*) from ar_payments where bank_transaction_id = ? and status = 'REVERSED'",
				Long.class, scenario.bankTransactionId());
		assertThat(reversedPayments).isEqualTo(1);
		Long historicalGroups = jdbcTemplate.queryForObject(
				"select count(*) from reconciliation_match_groups g "
						+ "join reconciliation_match_group_items i on i.group_id = g.id "
						+ "where i.bank_transaction_id = ?",
				Long.class, scenario.bankTransactionId());
		assertThat(historicalGroups).isGreaterThanOrEqualTo(1);
	}

	@Test
	void doubleUnmatch_isIdempotent() throws Exception {
		CreditScenario scenario = seedCreditLine("200.00");
		UUID customerId = createCustomer();
		UUID invoiceId = createAndIssueInvoice(customerId, BigDecimal.valueOf(200));
		confirmInvoicePayment(scenario, customerId, invoiceId, "200");
		unmatch(scenario);
		unmatch(scenario);
		assertThat(bankStatus(scenario.bankTransactionId())).isIn("UNMATCHED", "SUGGESTED");
		Long reversed = jdbcTemplate.queryForObject(
				"select count(*) from ar_payments where bank_transaction_id = ? and status = 'REVERSED'",
				Long.class, scenario.bankTransactionId());
		assertThat(reversed).isEqualTo(1);
	}

	@Test
	void partialPaymentReversal_onlyReversesSelectedBankPayment() throws Exception {
		CreditScenario bankA = seedCreditLine("300.00");
		importCreditLine(bankA.clientId(), bankA.bankAccountId(), "300.00", "REF-2");
		List<BankTransactionResponse> txns = listTransactions(bankA.clientId(), bankA.bankAccountId());
		UUID bankBId = txns.stream().filter(t -> !t.id().equals(bankA.bankTransactionId())).findFirst().orElseThrow().id();

		UUID customerId = createCustomer();
		UUID invoiceId = createAndIssueInvoice(customerId, BigDecimal.valueOf(1000));
		confirmInvoicePayment(bankA, customerId, invoiceId, "300");
		CreditScenario bankB = new CreditScenario(bankA.clientId(), bankBId, bankA.bankAccountId(), bankA.categoryId());
		confirmInvoicePayment(bankB, customerId, invoiceId, "300");

		JsonNode partial = getInvoice(invoiceId);
		assertThat(partial.get("settlementStatus").asText()).isEqualTo("PARTIALLY_PAID");
		assertThat(partial.get("outstanding").decimalValue()).isEqualByComparingTo(BigDecimal.valueOf(400));

		unmatch(bankA);
		JsonNode after = getInvoice(invoiceId);
		assertThat(after.get("settlementStatus").asText()).isEqualTo("PARTIALLY_PAID");
		assertThat(after.get("outstanding").decimalValue()).isEqualByComparingTo(BigDecimal.valueOf(700));

		Long activeOnB = jdbcTemplate.queryForObject(
				"select count(*) from ar_payments where bank_transaction_id = ? and status <> 'REVERSED'",
				Long.class, bankBId);
		assertThat(activeOnB).isEqualTo(1);
	}

	@Test
	void rematchSameInvoice_afterReversal_succeeds() throws Exception {
		CreditScenario scenario = seedCreditLine("450.00");
		UUID customerId = createCustomer();
		UUID invoiceId = createAndIssueInvoice(customerId, BigDecimal.valueOf(450));
		confirmInvoicePayment(scenario, customerId, invoiceId, "450");
		unmatch(scenario);
		confirmInvoicePayment(scenario, customerId, invoiceId, "450");
		assertThat(bankStatus(scenario.bankTransactionId())).isEqualTo("MATCHED");
		Long activeClaims = jdbcTemplate.queryForObject(
				"select count(*) from reconciliation_match_group_items "
						+ "where bank_transaction_id = ? and bank_claim_active = true",
				Long.class, scenario.bankTransactionId());
		assertThat(activeClaims).isEqualTo(1);
		Long confirmedGroups = jdbcTemplate.queryForObject(
				"""
				select count(*) from reconciliation_match_groups g
				join reconciliation_match_group_items i on i.group_id = g.id
				where i.bank_transaction_id = ? and g.status = 'CONFIRMED'
				""",
				Long.class, scenario.bankTransactionId());
		assertThat(confirmedGroups).isEqualTo(1);
	}

	@Test
	void rematchDifferentInvoice_afterReversal_succeeds() throws Exception {
		CreditScenario scenario = seedCreditLine("800.00");
		UUID customerId = createCustomer();
		UUID invoiceA = createAndIssueInvoice(customerId, BigDecimal.valueOf(800));
		UUID invoiceB = createAndIssueInvoice(customerId, BigDecimal.valueOf(800));
		confirmInvoicePayment(scenario, customerId, invoiceA, "800");
		unmatch(scenario);
		confirmInvoicePayment(scenario, customerId, invoiceB, "800");
		assertThat(bankStatus(scenario.bankTransactionId())).isEqualTo("MATCHED");
		assertThat(getInvoice(invoiceA).get("settlementStatus").asText()).isEqualTo("UNPAID");
		assertThat(getInvoice(invoiceB).get("settlementStatus").asText()).isEqualTo("PAID");
	}

	@Test
	void concurrentUnmatchAndRematch_serializesSafely() throws Exception {
		CreditScenario scenario = seedCreditLine("275.00");
		UUID customerId = createCustomer();
		UUID invoiceId = createAndIssueInvoice(customerId, BigDecimal.valueOf(275));
		confirmInvoicePayment(scenario, customerId, invoiceId, "275");

		ExecutorService pool = Executors.newFixedThreadPool(2);
		CountDownLatch ready = new CountDownLatch(2);
		CountDownLatch start = new CountDownLatch(1);
		Callable<Integer> unmatchTask = () -> {
			ready.countDown();
			start.await();
			return mockMvc.perform(post(unmatchPath(scenario))
							.header(HttpHeaders.AUTHORIZATION, IntegrationTestSupport.bearer(admin.token())))
					.andReturn().getResponse().getStatus();
		};
		Callable<Integer> rematchTask = () -> {
			ready.countDown();
			start.await();
			return mockMvc.perform(post(confirmInvoicePath(scenario))
							.header(HttpHeaders.AUTHORIZATION, IntegrationTestSupport.bearer(admin.token()))
							.contentType(MediaType.APPLICATION_JSON)
							.content(invoicePaymentBody(customerId, invoiceId, "275")))
					.andReturn().getResponse().getStatus();
		};
		Future<Integer> unmatchFuture = pool.submit(unmatchTask);
		Future<Integer> rematchFuture = pool.submit(rematchTask);
		ready.await();
		start.countDown();
		unmatchFuture.get();
		rematchFuture.get();
		pool.shutdownNow();

		Long activePayments = jdbcTemplate.queryForObject(
				"select count(*) from ar_payments where bank_transaction_id = ? and status <> 'REVERSED'",
				Long.class, scenario.bankTransactionId());
		assertThat(activePayments).isLessThanOrEqualTo(1);
		Long activeClaims = jdbcTemplate.queryForObject(
				"select count(*) from reconciliation_match_group_items "
						+ "where bank_transaction_id = ? and bank_claim_active = true",
				Long.class, scenario.bankTransactionId());
		assertThat(activeClaims).isLessThanOrEqualTo(1);
	}

	@Test
	void afterReversal_bankCanCreateIncomeFromCreditLine() throws Exception {
		CreditScenario credit = seedCreditLine("125.00");
		UUID customerId = createCustomer();
		UUID invoiceId = createAndIssueInvoice(customerId, BigDecimal.valueOf(125));
		confirmInvoicePayment(credit, customerId, invoiceId, "125");
		unmatch(credit);
		MvcResult result = mockMvc.perform(post("/api/v1/clients/" + credit.clientId() + "/bank/transactions/"
						+ credit.bankTransactionId() + "/create-income")
						.header(HttpHeaders.AUTHORIZATION, IntegrationTestSupport.bearer(admin.token()))
						.header(IDEMPOTENCY, IntegrationTestSupport.newIdempotencyKey())
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"categoryId\":\"" + credit.categoryId() + "\"}"))
				.andExpect(status().isCreated())
				.andReturn();
		assertThat(result.getResponse().getStatus()).isEqualTo(201);
	}

	@Test
	void unmatchInClosedPeriod_rejected() throws Exception {
		LocalDate txnDate = LocalDate.now().withDayOfMonth(Math.min(18, LocalDate.now().lengthOfMonth()));
		CreditScenario scenario = seedCreditLineOnDate("90.00", txnDate);
		UUID customerId = createCustomer();
		UUID invoiceId = createAndIssueInvoice(customerId, BigDecimal.valueOf(90));
		confirmInvoicePayment(scenario, customerId, invoiceId, "90");
		PeriodResponse period = getOrCreatePeriod(scenario.clientId(), txnDate.getYear(), txnDate.getMonthValue());
		closePeriod(scenario.clientId(), period.id());

		MvcResult blocked = mockMvc.perform(post(unmatchPath(scenario))
						.header(HttpHeaders.AUTHORIZATION, IntegrationTestSupport.bearer(admin.token())))
				.andExpect(status().isUnprocessableEntity())
				.andReturn();
		JsonNode problem = objectMapper.readTree(blocked.getResponse().getContentAsString());
		assertThat(problem.get("errorCode").asText()).isEqualTo("PERIOD_CLOSED");
		assertThat(bankStatus(scenario.bankTransactionId())).isEqualTo("MATCHED");
	}

	@Test
	void crossClientUnmatch_notFound() throws Exception {
		IntegrationTestSupport.Session other = support.registerFirmAdmin("Other Invoice Firm");
		CreditScenario victim = seedCreditLineFor(other, "60.00");
		UUID customerId = createCustomerFor(other);
		UUID invoiceId = createAndIssueInvoiceFor(other, customerId, BigDecimal.valueOf(60));
		confirmInvoicePaymentFor(other, victim, customerId, invoiceId, "60");

		ClientResponse attackerClient = createClient("Attacker Client");
		mockMvc.perform(post("/api/v1/clients/" + attackerClient.id() + "/bank/transactions/"
						+ victim.bankTransactionId() + "/unmatch")
						.header(HttpHeaders.AUTHORIZATION, IntegrationTestSupport.bearer(admin.token())))
				.andExpect(status().isNotFound());
	}

	@Test
	void unmatchInvoicePayment_writesAuditWithPaymentMetadata() throws Exception {
		CreditScenario scenario = seedCreditLine("180.00");
		UUID customerId = createCustomer();
		UUID invoiceId = createAndIssueInvoice(customerId, BigDecimal.valueOf(180));
		confirmInvoicePayment(scenario, customerId, invoiceId, "180");
		unmatch(scenario);
		Integer audits = jdbcTemplate.queryForObject("""
				select count(*) from audit_log
				where action = 'RECONCILIATION_REMOVED' and resource_id = ?
				""", Integer.class, scenario.bankTransactionId());
		assertThat(audits).isGreaterThanOrEqualTo(1);
	}

	private record CreditScenario(UUID clientId, UUID bankTransactionId, UUID bankAccountId, UUID categoryId) {
	}

	private void confirmInvoicePayment(CreditScenario scenario, UUID customerId, UUID invoiceId, String amount)
			throws Exception {
		mockMvc.perform(post(confirmInvoicePath(scenario))
						.header(HttpHeaders.AUTHORIZATION, IntegrationTestSupport.bearer(admin.token()))
						.contentType(MediaType.APPLICATION_JSON)
						.content(invoicePaymentBody(customerId, invoiceId, amount)))
				.andExpect(status().isOk());
	}

	private void confirmInvoicePaymentFor(
			IntegrationTestSupport.Session session,
			CreditScenario scenario,
			UUID customerId,
			UUID invoiceId,
			String amount
	) throws Exception {
		mockMvc.perform(post(confirmInvoicePath(scenario))
						.header(HttpHeaders.AUTHORIZATION, IntegrationTestSupport.bearer(session.token()))
						.contentType(MediaType.APPLICATION_JSON)
						.content(invoicePaymentBody(customerId, invoiceId, amount)))
				.andExpect(status().isOk());
	}

	private static String invoicePaymentBody(UUID customerId, UUID invoiceId, String amount) {
		return "{\"customerId\":\"" + customerId + "\",\"allocations\":[{\"invoiceId\":\""
				+ invoiceId + "\",\"amount\":" + amount + "}]}";
	}

	private static String confirmInvoicePath(CreditScenario scenario) {
		return "/api/v1/clients/" + scenario.clientId() + "/bank/transactions/"
				+ scenario.bankTransactionId() + "/confirm-invoice-payment";
	}

	private static String unmatchPath(CreditScenario scenario) {
		return "/api/v1/clients/" + scenario.clientId() + "/bank/transactions/"
				+ scenario.bankTransactionId() + "/unmatch";
	}

	private void unmatch(CreditScenario scenario) throws Exception {
		mockMvc.perform(post(unmatchPath(scenario))
						.header(HttpHeaders.AUTHORIZATION, IntegrationTestSupport.bearer(admin.token())))
				.andExpect(status().isOk());
	}

	private UUID createCustomer() throws Exception {
		return createCustomerFor(admin);
	}

	private UUID createCustomerFor(IntegrationTestSupport.Session session) throws Exception {
		MvcResult result = mockMvc.perform(post("/api/v1/ar/customers")
						.header(HttpHeaders.AUTHORIZATION, IntegrationTestSupport.bearer(session.token()))
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"name\":\"Slice7 Customer\",\"email\":\"s7@test.com\",\"paymentTermsDays\":30}"))
				.andExpect(status().isCreated())
				.andReturn();
		return UUID.fromString(objectMapper.readTree(result.getResponse().getContentAsString()).get("id").asText());
	}

	private UUID createAndIssueInvoice(UUID customerId, BigDecimal total) throws Exception {
		return createAndIssueInvoiceFor(admin, customerId, total);
	}

	private UUID createAndIssueInvoiceFor(IntegrationTestSupport.Session session, UUID customerId, BigDecimal total)
			throws Exception {
		MvcResult draft = mockMvc.perform(post("/api/v1/ar/invoices")
						.header(HttpHeaders.AUTHORIZATION, IntegrationTestSupport.bearer(session.token()))
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"customerId":"%s","lines":[{"description":"Service","quantity":1,"unitPrice":%s}]}
								""".formatted(customerId, total.toPlainString())))
				.andExpect(status().isCreated())
				.andReturn();
		UUID invoiceId = UUID.fromString(objectMapper.readTree(draft.getResponse().getContentAsString()).get("id").asText());
		mockMvc.perform(post("/api/v1/ar/invoices/{id}/issue", invoiceId)
						.header(HttpHeaders.AUTHORIZATION, IntegrationTestSupport.bearer(session.token()))
						.header(IDEMPOTENCY, IntegrationTestSupport.newIdempotencyKey()))
				.andExpect(status().isOk());
		return invoiceId;
	}

	private JsonNode getInvoice(UUID invoiceId) throws Exception {
		String json = mockMvc.perform(get("/api/v1/ar/invoices/{id}", invoiceId)
						.header(HttpHeaders.AUTHORIZATION, IntegrationTestSupport.bearer(admin.token())))
				.andExpect(status().isOk())
				.andReturn().getResponse().getContentAsString();
		return objectMapper.readTree(json);
	}

	private CreditScenario seedCreditLine(String amount) throws Exception {
		LocalDate txnDate = LocalDate.now().withDayOfMonth(Math.min(22, LocalDate.now().lengthOfMonth()));
		return seedCreditLineOnDate(amount, txnDate);
	}

	private CreditScenario seedCreditLineOnDate(String amount, LocalDate txnDate) throws Exception {
		ClientResponse client = createClient("Credit " + UUID.randomUUID().toString().substring(0, 5));
		UUID categoryId = createIncomeCategory();
		BankAccountResponse account = createBankAccount(client.id());
		importCreditCsv(client.id(), account.id(), amount, txnDate, "REF-1");
		UUID bankTxnId = listTransactions(client.id(), account.id()).get(0).id();
		return new CreditScenario(client.id(), bankTxnId, account.id(), categoryId);
	}

	private CreditScenario seedCreditLineFor(IntegrationTestSupport.Session session, String amount) throws Exception {
		LocalDate txnDate = LocalDate.now().withDayOfMonth(Math.min(22, LocalDate.now().lengthOfMonth()));
		ClientResponse client = createClientFor(session, "Victim");
		UUID categoryId = createIncomeCategoryFor(session);
		BankAccountResponse account = createBankAccountFor(session, client.id());
		importCreditCsvFor(session, client.id(), account.id(), amount, txnDate, "V-REF");
		UUID bankTxnId = listTransactionsFor(session, client.id(), account.id()).get(0).id();
		return new CreditScenario(client.id(), bankTxnId, account.id(), categoryId);
	}

	private void importCreditLine(UUID clientId, UUID bankAccountId, String amount, String ref) throws Exception {
		LocalDate txnDate = LocalDate.now().withDayOfMonth(Math.min(22, LocalDate.now().lengthOfMonth()));
		importCreditCsv(clientId, bankAccountId, amount, txnDate, ref);
	}

	private void importCreditCsv(UUID clientId, UUID bankAccountId, String amount, LocalDate txnDate, String ref)
			throws Exception {
		String csv = "Date,Description,Reference,Debit,Credit,Balance\n"
				+ txnDate + ",PAYMENT," + ref + ",," + amount + ",11000.00\n";
		importCsv(clientId, bankAccountId, "credit.csv", csv);
	}

	private void importCreditCsvFor(
			IntegrationTestSupport.Session session,
			UUID clientId,
			UUID bankAccountId,
			String amount,
			LocalDate txnDate,
			String ref
	) throws Exception {
		String csv = "Date,Description,Reference,Debit,Credit,Balance\n"
				+ txnDate + ",PAYMENT," + ref + ",," + amount + ",11000.00\n";
		importCsvFor(session, clientId, bankAccountId, "credit.csv", csv);
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

	private UUID createIncomeCategory() throws Exception {
		return createIncomeCategoryFor(admin);
	}

	private UUID createIncomeCategoryFor(IntegrationTestSupport.Session session) throws Exception {
		MvcResult result = mockMvc.perform(post("/api/v1/categories")
						.header(HttpHeaders.AUTHORIZATION, IntegrationTestSupport.bearer(session.token()))
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"code\":\"S7-IN-" + UUID.randomUUID().toString().substring(0, 6)
								+ "\",\"name\":\"Sales\",\"categoryType\":\"INCOME\"}"))
				.andExpect(status().isCreated())
				.andReturn();
		return UUID.fromString(objectMapper.readTree(result.getResponse().getContentAsString()).get("id").asText());
	}

	private BankAccountResponse createBankAccount(UUID clientId) throws Exception {
		return createBankAccountFor(admin, clientId);
	}

	private BankAccountResponse createBankAccountFor(IntegrationTestSupport.Session session, UUID clientId)
			throws Exception {
		MvcResult result = mockMvc.perform(post("/api/v1/clients/" + clientId + "/bank/accounts")
						.header(HttpHeaders.AUTHORIZATION, IntegrationTestSupport.bearer(session.token()))
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{
								  "bankName": "Slice7 Bank",
								  "accountName": "Operating",
								  "maskedAccountNumber": "****7777",
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

	private List<BankTransactionResponse> listTransactions(UUID clientId, UUID bankAccountId) throws Exception {
		return listTransactionsFor(admin, clientId, bankAccountId);
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

	private List<BankTransactionResponse> parseTransactionPage(MvcResult result) throws Exception {
		JsonNode page = objectMapper.readTree(result.getResponse().getContentAsString());
		List<BankTransactionResponse> rows = new ArrayList<>();
		for (JsonNode node : page.get("content")) {
			rows.add(objectMapper.treeToValue(node, BankTransactionResponse.class));
		}
		return rows;
	}

	private String bankStatus(UUID bankTransactionId) {
		return jdbcTemplate.queryForObject(
				"select match_status from bank_transactions where id = ?",
				String.class,
				bankTransactionId);
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
						.header(IDEMPOTENCY, IntegrationTestSupport.newIdempotencyKey())
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"closeNote\":\"Slice7 close\"}"))
				.andExpect(status().isOk())
				.andReturn();
		return support.read(result, PeriodResponse.class);
	}
}
