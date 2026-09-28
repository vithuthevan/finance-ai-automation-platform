package com.finance.platform.finance;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.finance.platform.auth.application.dto.CreateUserRequest;
import com.finance.platform.finance.application.dto.BankAccountResponse;
import com.finance.platform.finance.application.dto.BankTransactionResponse;
import com.finance.platform.finance.application.dto.CategoryResponse;
import com.finance.platform.finance.application.dto.ClientResponse;
import com.finance.platform.finance.application.dto.CreateExpenseRequest;
import com.finance.platform.finance.application.dto.ExpenseResponse;
import com.finance.platform.finance.application.dto.IncomeResponse;
import com.finance.platform.finance.application.dto.PeriodResponse;
import com.finance.platform.finance.application.dto.UpdateExpenseRequest;
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
import java.time.YearMonth;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Slice 8 — closed-period financial integrity (PostgreSQL / Testcontainers).
 */
class ClosedPeriodIntegrityIntegrationTest extends AbstractPostgresIntegrationTest {

	private static final String IDEMPOTENCY = "Idempotency-Key";

	@Autowired
	private ObjectMapper objectMapper;

	@Autowired
	private JdbcTemplate jdbcTemplate;

	private IntegrationTestSupport.Session admin;

	@BeforeEach
	void setUp() throws Exception {
		support.seedRolesIfNeeded();
		admin = support.registerFirmAdmin("Closed Period Firm");
	}

	@Test
	void closedPeriod_blocksExpenseFinancialMutations() throws Exception {
		ReadyClosedMonth ready = prepareReadyClosedMonth("exp-mut");
		assertProblem(postExpenseInMonth(ready.clientId(), ready.expenseCategoryId(), ready.closedDate(), "50.00"),
				"PERIOD_CLOSED");
		assertProblem(voidExpense(ready.clientId(), ready.closedMonthApprovedExpenseId()), "PERIOD_CLOSED");
	}

	@Test
	void closedPeriod_blocksIncomeFinancialMutations() throws Exception {
		ReadyClosedMonth ready = prepareReadyClosedMonth("inc-mut");
		assertProblem(postIncomeInMonth(ready.clientId(), ready.incomeCategoryId(), ready.closedDate(), "75.00"),
				"PERIOD_CLOSED");
		assertProblem(voidIncome(ready.clientId(), ready.closedMonthApprovedIncomeId()), "PERIOD_CLOSED");
	}

	@Test
	void dateMoveIntoClosedPeriod_blocked() throws Exception {
		ReadyClosedMonth ready = prepareReadyClosedMonth("date-in");
		MvcResult blocked = mockMvc.perform(put("/api/v1/clients/" + ready.clientId() + "/expenses/" + ready.openMonthExpenseId())
						.header(HttpHeaders.AUTHORIZATION, IntegrationTestSupport.bearer(admin.token()))
						.contentType(MediaType.APPLICATION_JSON)
						.content(objectMapper.writeValueAsString(new UpdateExpenseRequest(
								ready.closedDate(),
								ready.expenseCategoryId(),
								new BigDecimal("100.00"),
								"LKR",
								"Moved Vendor",
								null,
								null,
								null
						))))
				.andExpect(status().isUnprocessableEntity())
				.andReturn();
		assertThat(objectMapper.readTree(blocked.getResponse().getContentAsString()).get("errorCode").asText())
				.isEqualTo("PERIOD_CLOSED");
	}

	@Test
	void reportAffectingCategoryChangeIntoClosedMonth_blocked() throws Exception {
		ReadyClosedMonth ready = prepareReadyClosedMonth("cat-chg");
		CategoryResponse alt = createCategory("ALT-" + UUID.randomUUID().toString().substring(0, 5), "Travel", "EXPENSE");
		MvcResult blocked = mockMvc.perform(put("/api/v1/clients/" + ready.clientId() + "/expenses/" + ready.openMonthExpenseId())
						.header(HttpHeaders.AUTHORIZATION, IntegrationTestSupport.bearer(admin.token()))
						.contentType(MediaType.APPLICATION_JSON)
						.content(objectMapper.writeValueAsString(new UpdateExpenseRequest(
								ready.closedDate(),
								alt.id(),
								new BigDecimal("100.00"),
								"LKR",
								"Category Vendor",
								null,
								null,
								null
						))))
				.andExpect(status().isUnprocessableEntity())
				.andReturn();
		assertThat(objectMapper.readTree(blocked.getResponse().getContentAsString()).get("errorCode").asText())
				.isEqualTo("PERIOD_CLOSED");
	}

	@Test
	void bankUnmatchAndCreateFromBank_blockedInClosedPeriod() throws Exception {
		ReadyClosedMonth ready = prepareReadyClosedMonth("bank");
		MvcResult unmatch = mockMvc.perform(post("/api/v1/clients/" + ready.clientId() + "/bank/transactions/"
						+ ready.matchedBankTransactionId() + "/unmatch")
						.header(HttpHeaders.AUTHORIZATION, IntegrationTestSupport.bearer(admin.token())))
				.andReturn();
		assertProblem(unmatch, "PERIOD_CLOSED");
		assertProblem(createExpenseFromBank(ready.clientId(), ready.unmatchedBankTransactionId(), ready.expenseCategoryId()),
				"PERIOD_CLOSED");
	}

	@Test
	void invoicePaymentConfirm_blockedInClosedPeriod() throws Exception {
		ReadyClosedMonth ready = prepareReadyClosedMonth("inv-pay");
		UUID customerId = createCustomer(ready.clientId());
		UUID invoiceId = createAndIssueInvoice(customerId, BigDecimal.valueOf(100));
		assertProblem(confirmInvoicePayment(ready.clientId(), ready.invoicePaymentBankId(), customerId, invoiceId, "100"),
				"PERIOD_CLOSED");
	}

	@Test
	void directBankLinkedArReverse_blocked() throws Exception {
		ReadyClosedMonth ready = prepareReadyOpenMonth("ar-rev");
		importCreditCsv(ready.clientId(), ready.bankAccountId(), ready.closedDate(), "120.00", "AR-REV");
		List<BankTransactionResponse> credits = listTransactions(ready.clientId(), ready.bankAccountId()).stream()
				.filter(t -> "AR-REV".equals(t.referenceNo()))
				.toList();
		UUID creditBankId = credits.get(0).id();
		UUID customerId = createCustomer(ready.clientId());
		UUID invoiceId = createAndIssueInvoice(customerId, BigDecimal.valueOf(120));
		confirmInvoicePayment(ready.clientId(), creditBankId, customerId, invoiceId, "120");
		UUID paymentId = jdbcTemplate.queryForObject(
				"select id from ar_payments where bank_transaction_id = ? and status <> 'REVERSED' limit 1",
				UUID.class,
				creditBankId);
		MvcResult blocked = mockMvc.perform(post("/api/v1/ar/payments/" + paymentId + "/reverse")
						.header(HttpHeaders.AUTHORIZATION, IntegrationTestSupport.bearer(admin.token()))
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"reason\":\"Direct reverse attempt\"}"))
				.andExpect(status().isUnprocessableEntity())
				.andReturn();
		assertThat(objectMapper.readTree(blocked.getResponse().getContentAsString()).get("errorCode").asText())
				.isEqualTo("AR_PAYMENT_REVERSE_REQUIRES_BANK_UNMATCH");
	}

	@Test
	void reopen_allowsMutationThenCloseAgain() throws Exception {
		ReadyClosedMonth ready = prepareReadyClosedMonth("reopen");
		assertProblem(postExpenseInMonth(ready.clientId(), ready.expenseCategoryId(), ready.closedDate(), "10.00"),
				"PERIOD_CLOSED");

		mockMvc.perform(post("/api/v1/clients/" + ready.clientId() + "/periods/" + ready.periodId() + "/reopen")
						.header(HttpHeaders.AUTHORIZATION, IntegrationTestSupport.bearer(admin.token()))
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"reason\":\"Audit correction\"}"))
				.andExpect(status().isOk());

		mockMvc.perform(post("/api/v1/clients/" + ready.clientId() + "/expenses")
						.header(HttpHeaders.AUTHORIZATION, IntegrationTestSupport.bearer(admin.token()))
						.header(IDEMPOTENCY, IntegrationTestSupport.newIdempotencyKey())
						.contentType(MediaType.APPLICATION_JSON)
						.content(objectMapper.writeValueAsString(new CreateExpenseRequest(
								ready.closedDate(),
								ready.expenseCategoryId(),
								new BigDecimal("10.00"),
								"LKR",
								"After Reopen",
								null,
								null,
								null
						))))
				.andExpect(status().isCreated());

		Integer reopenedAudits = jdbcTemplate.queryForObject(
				"select count(*) from audit_log where action = 'PERIOD_REOPENED' and resource_id = ?",
				Integer.class,
				ready.periodId());
		assertThat(reopenedAudits).isGreaterThanOrEqualTo(1);
	}

	@Test
	void businessOwnerCannotClosePeriod() throws Exception {
		ReadyClosedMonth ready = prepareReadyOpenMonth("auth-close");
		String ownerToken = createStaffAndLogin("BUSINESS_OWNER", List.of(ready.clientId()));
		mockMvc.perform(post("/api/v1/clients/" + ready.clientId() + "/periods/" + ready.periodId() + "/close")
						.header(HttpHeaders.AUTHORIZATION, IntegrationTestSupport.bearer(ownerToken))
						.header(IDEMPOTENCY, IntegrationTestSupport.newIdempotencyKey())
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"closeNote\":\"Owner close\"}"))
				.andExpect(status().isForbidden());
	}

	@Test
	void reportStability_afterRejectedMutations() throws Exception {
		ReadyClosedMonth ready = prepareReadyClosedMonth("report");
		JsonNode before = profitAndLoss(ready.clientId(), ready.closedMonthStart(), ready.closedMonthEnd());
		BigDecimal beforeExpenses = before.get("totalExpenses").decimalValue();
		BigDecimal beforeNet = before.get("netResult").decimalValue();

		assertProblem(postExpenseInMonth(ready.clientId(), ready.expenseCategoryId(), ready.closedDate(), "9999.00"),
				"PERIOD_CLOSED");
		assertProblem(voidExpense(ready.clientId(), ready.closedMonthApprovedExpenseId()), "PERIOD_CLOSED");

		JsonNode after = profitAndLoss(ready.clientId(), ready.closedMonthStart(), ready.closedMonthEnd());
		assertThat(after.get("totalExpenses").decimalValue()).isEqualByComparingTo(beforeExpenses);
		assertThat(after.get("netResult").decimalValue()).isEqualByComparingTo(beforeNet);
	}

	@Test
	void closeVersusVoidExpense_serializesWithoutPostCloseMutation() throws Exception {
		ReadyClosedMonth ready = prepareReadyOpenMonth("conc-void");
		ExecutorService pool = Executors.newFixedThreadPool(2);
		CountDownLatch readyLatch = new CountDownLatch(2);
		CountDownLatch start = new CountDownLatch(1);
		Callable<Integer> closeTask = () -> {
			readyLatch.countDown();
			start.await();
			return createMockMvc().perform(post("/api/v1/clients/" + ready.clientId() + "/periods/" + ready.periodId() + "/close")
							.header(HttpHeaders.AUTHORIZATION, IntegrationTestSupport.bearer(admin.token()))
							.header(IDEMPOTENCY, IntegrationTestSupport.newIdempotencyKey())
							.contentType(MediaType.APPLICATION_JSON)
							.content("{\"closeNote\":\"Concurrent close\"}"))
					.andReturn().getResponse().getStatus();
		};
		Callable<Integer> voidTask = () -> {
			readyLatch.countDown();
			start.await();
			return createMockMvc().perform(post("/api/v1/clients/" + ready.clientId() + "/expenses/"
							+ ready.closedMonthApprovedExpenseId() + "/void")
							.header(HttpHeaders.AUTHORIZATION, IntegrationTestSupport.bearer(admin.token()))
							.header(IDEMPOTENCY, IntegrationTestSupport.newIdempotencyKey())
							.contentType(MediaType.APPLICATION_JSON)
							.content("{\"reason\":\"Concurrent void\"}"))
					.andReturn().getResponse().getStatus();
		};
		Future<Integer> closeFuture = pool.submit(closeTask);
		Future<Integer> voidFuture = pool.submit(voidTask);
		readyLatch.await();
		start.countDown();
		int closeStatus = closeFuture.get();
		int voidStatus = voidFuture.get();
		pool.shutdownNow();

		assertThat(closeStatus == 200 || voidStatus == 200).isTrue();
		if (closeStatus == 200) {
			assertThat(voidStatus).isEqualTo(422);
			assertThat(periodStatus(ready.periodId())).isEqualTo("CLOSED");
			assertThat(expenseStatus(ready.closedMonthApprovedExpenseId())).isEqualTo("APPROVED");
		} else {
			assertThat(closeStatus).isEqualTo(422);
			assertThat(expenseStatus(ready.closedMonthApprovedExpenseId())).isEqualTo("VOID");
		}
	}

	@Test
	void closeVersusBankUnmatch_serializesWithoutPostCloseUnmatch() throws Exception {
		ReadyClosedMonth ready = prepareReadyOpenMonth("conc-unmatch");
		ExecutorService pool = Executors.newFixedThreadPool(2);
		CountDownLatch readyLatch = new CountDownLatch(2);
		CountDownLatch start = new CountDownLatch(1);
		Callable<Integer> closeTask = () -> {
			readyLatch.countDown();
			start.await();
			return createMockMvc().perform(post("/api/v1/clients/" + ready.clientId() + "/periods/" + ready.periodId() + "/close")
							.header(HttpHeaders.AUTHORIZATION, IntegrationTestSupport.bearer(admin.token()))
							.header(IDEMPOTENCY, IntegrationTestSupport.newIdempotencyKey())
							.contentType(MediaType.APPLICATION_JSON)
							.content("{\"closeNote\":\"Concurrent close\"}"))
					.andReturn().getResponse().getStatus();
		};
		Callable<Integer> unmatchTask = () -> {
			readyLatch.countDown();
			start.await();
			return createMockMvc().perform(post("/api/v1/clients/" + ready.clientId() + "/bank/transactions/"
							+ ready.matchedBankTransactionId() + "/unmatch")
							.header(HttpHeaders.AUTHORIZATION, IntegrationTestSupport.bearer(admin.token())))
					.andReturn().getResponse().getStatus();
		};
		Future<Integer> closeFuture = pool.submit(closeTask);
		Future<Integer> unmatchFuture = pool.submit(unmatchTask);
		readyLatch.await();
		start.countDown();
		int closeStatus = closeFuture.get();
		int unmatchStatus = unmatchFuture.get();
		pool.shutdownNow();

		assertThat(closeStatus == 200 || unmatchStatus == 200).isTrue();
		if (closeStatus == 200) {
			assertThat(unmatchStatus).isEqualTo(422);
			assertThat(bankStatus(ready.matchedBankTransactionId())).isEqualTo("MATCHED");
		} else {
			assertThat(closeStatus).isEqualTo(422);
			assertThat(bankStatus(ready.matchedBankTransactionId())).isIn("UNMATCHED", "SUGGESTED");
		}
	}

	private record ReadyClosedMonth(
			UUID clientId,
			UUID periodId,
			UUID bankAccountId,
			LocalDate closedDate,
			LocalDate closedMonthStart,
			LocalDate closedMonthEnd,
			LocalDate openMonthDate,
			UUID expenseCategoryId,
			UUID incomeCategoryId,
			UUID closedMonthApprovedExpenseId,
			UUID closedMonthApprovedIncomeId,
			UUID openMonthExpenseId,
			UUID matchedBankTransactionId,
			UUID unmatchedBankTransactionId,
			UUID invoicePaymentBankId
	) {
	}

	private ReadyClosedMonth prepareReadyClosedMonth(String suffix) throws Exception {
		ReadyClosedMonth open = prepareReadyOpenMonth(suffix);
		closePeriod(open.clientId(), open.periodId());
		assertThat(periodStatus(open.periodId())).isEqualTo("CLOSED");
		importDebitCsv(open.clientId(), open.bankAccountId(), open.closedDate(), "100.00", "POST-CLOSE-BANK");
		importCreditCsv(open.clientId(), open.bankAccountId(), open.closedDate(), "100.00", "POST-CLOSE-CR");
		List<BankTransactionResponse> txns = listTransactions(open.clientId(), open.bankAccountId());
		UUID unmatchedDebit = txns.stream()
				.filter(t -> "POST-CLOSE-BANK".equals(t.referenceNo()))
				.findFirst()
				.orElseThrow()
				.id();
		UUID unmatchedCredit = txns.stream()
				.filter(t -> "POST-CLOSE-CR".equals(t.referenceNo()))
				.findFirst()
				.orElseThrow()
				.id();
		return new ReadyClosedMonth(
				open.clientId(),
				open.periodId(),
				open.bankAccountId(),
				open.closedDate(),
				open.closedMonthStart(),
				open.closedMonthEnd(),
				open.openMonthDate(),
				open.expenseCategoryId(),
				open.incomeCategoryId(),
				open.closedMonthApprovedExpenseId(),
				open.closedMonthApprovedIncomeId(),
				open.openMonthExpenseId(),
				open.matchedBankTransactionId(),
				unmatchedDebit,
				unmatchedCredit
		);
	}

	private ReadyClosedMonth prepareReadyOpenMonth(String suffix) throws Exception {
		YearMonth closedMonth = YearMonth.now().minusMonths(1);
		YearMonth openMonth = YearMonth.now();
		LocalDate closedDate = closedMonth.atDay(Math.min(15, closedMonth.lengthOfMonth()));
		LocalDate openMonthDate = openMonth.atDay(Math.min(5, openMonth.lengthOfMonth()));
		String unique = UUID.randomUUID().toString().substring(0, 8);

		ClientResponse client = createClient("Closed " + suffix);
		CategoryResponse expenseCategory = createCategory("EXP-" + unique, "Ops Expense", "EXPENSE");
		CategoryResponse incomeCategory = createCategory("INC-" + unique, "Ops Income", "INCOME");

		PeriodResponse period = getOrCreatePeriod(client.id(), closedMonth.getYear(), closedMonth.getMonthValue());

		ExpenseResponse closedApproved = approveExpense(client.id(),
				createExpense(client.id(), expenseCategory.id(), "100.00", "Closed Approved", closedDate).id());
		IncomeResponse closedIncApproved = approveIncome(client.id(),
				createIncome(client.id(), incomeCategory.id(), "80.00", closedDate).id());

		ExpenseResponse openExpense = createExpense(client.id(), expenseCategory.id(), "100.00", "Open Month", openMonthDate);

		BankAccountResponse account = createBankAccount(client.id());
		importDebitCsv(client.id(), account.id(), closedDate, "100.00", "CLOSE-EXP");
		List<BankTransactionResponse> txns = listTransactions(client.id(), account.id());
		UUID matchedBankId = txns.stream()
				.filter(t -> "CLOSE-EXP".equals(t.referenceNo()))
				.findFirst()
				.orElseThrow()
				.id();
		confirmReconcileOk(client.id(), matchedBankId, closedApproved.id());

		return new ReadyClosedMonth(
				client.id(),
				period.id(),
				account.id(),
				closedDate,
				closedMonth.atDay(1),
				closedMonth.atEndOfMonth(),
				openMonthDate,
				expenseCategory.id(),
				incomeCategory.id(),
				closedApproved.id(),
				closedIncApproved.id(),
				openExpense.id(),
				matchedBankId,
				null,
				null
		);
	}

	private void assertProblem(MvcResult result, String errorCode) throws Exception {
		assertThat(result.getResponse().getStatus()).isEqualTo(422);
		assertThat(objectMapper.readTree(result.getResponse().getContentAsString()).get("errorCode").asText())
				.isEqualTo(errorCode);
	}

	private MvcResult postExpenseInMonth(UUID clientId, UUID categoryId, LocalDate date, String amount) throws Exception {
		return mockMvc.perform(post("/api/v1/clients/" + clientId + "/expenses")
						.header(HttpHeaders.AUTHORIZATION, IntegrationTestSupport.bearer(admin.token()))
						.header(IDEMPOTENCY, IntegrationTestSupport.newIdempotencyKey())
						.contentType(MediaType.APPLICATION_JSON)
						.content(objectMapper.writeValueAsString(new CreateExpenseRequest(
								date,
								categoryId,
								new BigDecimal(amount),
								"LKR",
								"Blocked Vendor",
								null,
								null,
								null
						))))
				.andReturn();
	}

	private MvcResult postIncomeInMonth(UUID clientId, UUID categoryId, LocalDate date, String amount) throws Exception {
		return mockMvc.perform(post("/api/v1/clients/" + clientId + "/income")
						.header(HttpHeaders.AUTHORIZATION, IntegrationTestSupport.bearer(admin.token()))
						.header(IDEMPOTENCY, IntegrationTestSupport.newIdempotencyKey())
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{
								  "transactionDate":"%s",
								  "categoryId":"%s",
								  "amount":%s,
								  "currencyCode":"LKR",
								  "customerName":"Blocked Customer",
								  "paymentMethod":"BANK_TRANSFER"
								}
								""".formatted(date, categoryId, amount)))
				.andReturn();
	}

	private void confirmReconcileOk(UUID clientId, UUID bankTxnId, UUID expenseId) throws Exception {
		mockMvc.perform(post("/api/v1/clients/" + clientId + "/bank/transactions/" + bankTxnId + "/confirm")
						.header(HttpHeaders.AUTHORIZATION, IntegrationTestSupport.bearer(admin.token()))
						.header(IDEMPOTENCY, IntegrationTestSupport.newIdempotencyKey())
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"expenseId\":\"" + expenseId + "\"}"))
				.andExpect(status().isOk());
	}

	private MvcResult approveExpenseRequest(UUID clientId, UUID expenseId) throws Exception {
		return mockMvc.perform(post("/api/v1/clients/" + clientId + "/expenses/" + expenseId + "/approve")
						.header(HttpHeaders.AUTHORIZATION, IntegrationTestSupport.bearer(admin.token()))
						.header(IDEMPOTENCY, IntegrationTestSupport.newIdempotencyKey()))
				.andReturn();
	}

	private MvcResult voidExpense(UUID clientId, UUID expenseId) throws Exception {
		return mockMvc.perform(post("/api/v1/clients/" + clientId + "/expenses/" + expenseId + "/void")
						.header(HttpHeaders.AUTHORIZATION, IntegrationTestSupport.bearer(admin.token()))
						.header(IDEMPOTENCY, IntegrationTestSupport.newIdempotencyKey())
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"reason\":\"Closed period void\"}"))
				.andReturn();
	}

	private MvcResult approveIncomeRequest(UUID clientId, UUID incomeId) throws Exception {
		return mockMvc.perform(post("/api/v1/clients/" + clientId + "/income/" + incomeId + "/approve")
						.header(HttpHeaders.AUTHORIZATION, IntegrationTestSupport.bearer(admin.token()))
						.header(IDEMPOTENCY, IntegrationTestSupport.newIdempotencyKey()))
				.andReturn();
	}

	private MvcResult voidIncome(UUID clientId, UUID incomeId) throws Exception {
		return mockMvc.perform(post("/api/v1/clients/" + clientId + "/income/" + incomeId + "/void")
						.header(HttpHeaders.AUTHORIZATION, IntegrationTestSupport.bearer(admin.token()))
						.header(IDEMPOTENCY, IntegrationTestSupport.newIdempotencyKey())
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"reason\":\"Closed period void\"}"))
				.andReturn();
	}

	private MvcResult confirmReconcile(UUID clientId, UUID bankTxnId, UUID expenseId) throws Exception {
		return mockMvc.perform(post("/api/v1/clients/" + clientId + "/bank/transactions/" + bankTxnId + "/confirm")
						.header(HttpHeaders.AUTHORIZATION, IntegrationTestSupport.bearer(admin.token()))
						.header(IDEMPOTENCY, IntegrationTestSupport.newIdempotencyKey())
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"expenseId\":\"" + expenseId + "\"}"))
				.andReturn();
	}

	private MvcResult createExpenseFromBank(UUID clientId, UUID bankTxnId, UUID categoryId) throws Exception {
		return mockMvc.perform(post("/api/v1/clients/" + clientId + "/bank/transactions/" + bankTxnId + "/create-expense")
						.header(HttpHeaders.AUTHORIZATION, IntegrationTestSupport.bearer(admin.token()))
						.header(IDEMPOTENCY, IntegrationTestSupport.newIdempotencyKey())
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"categoryId\":\"" + categoryId + "\"}"))
				.andReturn();
	}

	private MvcResult confirmInvoicePayment(
			UUID clientId,
			UUID bankTxnId,
			UUID customerId,
			UUID invoiceId,
			String amount
	) throws Exception {
		return mockMvc.perform(post("/api/v1/clients/" + clientId + "/bank/transactions/" + bankTxnId + "/confirm-invoice-payment")
						.header(HttpHeaders.AUTHORIZATION, IntegrationTestSupport.bearer(admin.token()))
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"customerId\":\"" + customerId + "\",\"allocations\":[{\"invoiceId\":\""
								+ invoiceId + "\",\"amount\":" + amount + "}]}"))
				.andReturn();
	}

	private JsonNode profitAndLoss(UUID clientId, LocalDate from, LocalDate to) throws Exception {
		MvcResult result = mockMvc.perform(get("/api/v1/clients/" + clientId + "/reports/profit-and-loss")
						.param("from", from.toString())
						.param("to", to.toString())
						.header(HttpHeaders.AUTHORIZATION, IntegrationTestSupport.bearer(admin.token())))
				.andExpect(status().isOk())
				.andReturn();
		return objectMapper.readTree(result.getResponse().getContentAsString());
	}

	private UUID createCustomer(UUID clientId) throws Exception {
		MvcResult result = mockMvc.perform(post("/api/v1/ar/customers")
						.header(HttpHeaders.AUTHORIZATION, IntegrationTestSupport.bearer(admin.token()))
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{
								  "name": "Slice8 Customer",
								  "email": "slice8@test.com",
								  "paymentTermsDays": 30,
								  "clientId": "%s"
								}
								""".formatted(clientId)))
				.andExpect(status().isCreated())
				.andReturn();
		return UUID.fromString(objectMapper.readTree(result.getResponse().getContentAsString()).get("id").asText());
	}

	private UUID createAndIssueInvoice(UUID customerId, BigDecimal total) throws Exception {
		MvcResult draft = mockMvc.perform(post("/api/v1/ar/invoices")
						.header(HttpHeaders.AUTHORIZATION, IntegrationTestSupport.bearer(admin.token()))
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"customerId":"%s","lines":[{"description":"Service","quantity":1,"unitPrice":%s}]}
								""".formatted(customerId, total.toPlainString())))
				.andExpect(status().isCreated())
				.andReturn();
		UUID invoiceId = UUID.fromString(objectMapper.readTree(draft.getResponse().getContentAsString()).get("id").asText());
		mockMvc.perform(post("/api/v1/ar/invoices/{id}/issue", invoiceId)
						.header(HttpHeaders.AUTHORIZATION, IntegrationTestSupport.bearer(admin.token()))
						.header(IDEMPOTENCY, IntegrationTestSupport.newIdempotencyKey()))
				.andExpect(status().isOk());
		return invoiceId;
	}

	private void importDebitCsv(UUID clientId, UUID bankAccountId, LocalDate txnDate, String amount, String ref)
			throws Exception {
		String csv = "Date,Description,Reference,Debit,Credit,Balance\n"
				+ txnDate + ",VENDOR," + ref + "," + amount + ",,50000.00\n";
		importCsv(clientId, bankAccountId, "debit.csv", csv);
	}

	private void importCreditCsv(UUID clientId, UUID bankAccountId, LocalDate txnDate, String amount, String ref)
			throws Exception {
		String csv = "Date,Description,Reference,Debit,Credit,Balance\n"
				+ txnDate + ",PAYMENT," + ref + ",," + amount + ",50000.00\n";
		importCsv(clientId, bankAccountId, "credit.csv", csv);
	}

	private void importCsv(UUID clientId, UUID bankAccountId, String fileName, String csv) throws Exception {
		MockMultipartFile file = new MockMultipartFile("file", fileName, "text/csv", csv.getBytes(StandardCharsets.UTF_8));
		mockMvc.perform(multipart("/api/v1/clients/" + clientId + "/bank/imports")
						.file(file)
						.param("bankAccountId", bankAccountId.toString())
						.header(HttpHeaders.AUTHORIZATION, IntegrationTestSupport.bearer(admin.token()))
						.header(IDEMPOTENCY, IntegrationTestSupport.newIdempotencyKey()))
				.andExpect(status().isCreated());
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
						.content("{\"code\":\"" + code + "\",\"name\":\"" + name + "\",\"categoryType\":\"" + type + "\"}"))
				.andExpect(status().isCreated())
				.andReturn();
		return support.read(result, CategoryResponse.class);
	}

	private ExpenseResponse createExpense(UUID clientId, UUID categoryId, String amount, String vendor, LocalDate date)
			throws Exception {
		MvcResult result = mockMvc.perform(post("/api/v1/clients/" + clientId + "/expenses")
						.header(HttpHeaders.AUTHORIZATION, IntegrationTestSupport.bearer(admin.token()))
						.header(IDEMPOTENCY, IntegrationTestSupport.newIdempotencyKey())
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

	private IncomeResponse createIncome(UUID clientId, UUID categoryId, String amount, LocalDate date)
			throws Exception {
		MvcResult result = mockMvc.perform(post("/api/v1/clients/" + clientId + "/income")
						.header(HttpHeaders.AUTHORIZATION, IntegrationTestSupport.bearer(admin.token()))
						.header(IDEMPOTENCY, IntegrationTestSupport.newIdempotencyKey())
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{
								  "transactionDate":"%s",
								  "categoryId":"%s",
								  "amount":%s,
								  "currencyCode":"LKR",
								  "customerName":"Closed Inc",
								  "paymentMethod":"BANK_TRANSFER"
								}
								""".formatted(date, categoryId, amount)))
				.andExpect(status().isCreated())
				.andReturn();
		return support.read(result, IncomeResponse.class);
	}

	private ExpenseResponse approveExpense(UUID clientId, UUID expenseId) throws Exception {
		MvcResult result = mockMvc.perform(post("/api/v1/clients/" + clientId + "/expenses/" + expenseId + "/approve")
						.header(HttpHeaders.AUTHORIZATION, IntegrationTestSupport.bearer(admin.token()))
						.header(IDEMPOTENCY, IntegrationTestSupport.newIdempotencyKey()))
				.andExpect(status().isOk())
				.andReturn();
		return support.read(result, ExpenseResponse.class);
	}

	private IncomeResponse approveIncome(UUID clientId, UUID incomeId) throws Exception {
		MvcResult result = mockMvc.perform(post("/api/v1/clients/" + clientId + "/income/" + incomeId + "/approve")
						.header(HttpHeaders.AUTHORIZATION, IntegrationTestSupport.bearer(admin.token()))
						.header(IDEMPOTENCY, IntegrationTestSupport.newIdempotencyKey()))
				.andExpect(status().isOk())
				.andReturn();
		return support.read(result, IncomeResponse.class);
	}

	private BankAccountResponse createBankAccount(UUID clientId) throws Exception {
		MvcResult result = mockMvc.perform(post("/api/v1/clients/" + clientId + "/bank/accounts")
						.header(HttpHeaders.AUTHORIZATION, IntegrationTestSupport.bearer(admin.token()))
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{
								  "bankName": "Slice8 Bank",
								  "accountName": "Operating",
								  "maskedAccountNumber": "****8888",
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
						.param("size", "50")
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
						.content("{\"closeNote\":\"Slice8 close\"}"))
				.andExpect(status().isOk())
				.andReturn();
		return support.read(result, PeriodResponse.class);
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

	private String periodStatus(UUID periodId) {
		return jdbcTemplate.queryForObject("select status from accounting_periods where id = ?", String.class, periodId);
	}

	private String expenseStatus(UUID expenseId) {
		return jdbcTemplate.queryForObject("select status from expenses where id = ?", String.class, expenseId);
	}

	private String bankStatus(UUID bankTransactionId) {
		return jdbcTemplate.queryForObject("select match_status from bank_transactions where id = ?", String.class, bankTransactionId);
	}
}
