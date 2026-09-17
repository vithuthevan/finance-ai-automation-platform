package com.finance.platform.workflow;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.finance.platform.finance.application.dto.BankAccountResponse;
import com.finance.platform.finance.application.dto.BankTransactionResponse;
import com.finance.platform.finance.application.dto.CategoryResponse;
import com.finance.platform.finance.application.dto.ClientResponse;
import com.finance.platform.finance.application.dto.DocumentResponse;
import com.finance.platform.finance.application.dto.ExpenseResponse;
import com.finance.platform.finance.application.dto.ModifySuggestionRequest;
import com.finance.platform.finance.application.dto.PeriodReadinessResponse;
import com.finance.platform.finance.application.dto.PeriodResponse;
import com.finance.platform.finance.domain.model.AccountingPeriod;
import com.finance.platform.ai.application.DocumentAiProcessor;
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
 * End-to-end business workflow: document upload → manual draft from document → approve →
 * bank import → reconcile → period close → P&amp;L report.
 */
class GoldenPathWorkflowIntegrationTest extends AbstractPostgresIntegrationTest {

	private static final String IDEMPOTENCY = "Idempotency-Key";

	@Autowired
	private ObjectMapper objectMapper;

	@Autowired
	private DocumentAiProcessor documentAiProcessor;

	private IntegrationTestSupport.Session admin;

	@BeforeEach
	void setUp() throws Exception {
		support.seedRolesIfNeeded();
		admin = support.registerFirmAdmin("Golden Path");
	}

	@Test
	void documentToCloseWorkflow_producesApprovedLedgerAndReport() throws Exception {
		ClientResponse client = createClient("Golden Path Client");
		CategoryResponse expenseCategory = createCategory("GP-FOOD", "Golden Path Food", "EXPENSE");
		LocalDate txnDate = LocalDate.now().withDayOfMonth(15);
		BigDecimal amount = new BigDecimal("4850.00");

		DocumentResponse document = uploadDocument(client.id());
		documentAiProcessor.process(document.id());
		waitForDocumentStatus(client.id(), document.id(), "NEEDS_REVIEW");

		createDraftFromDocument(client.id(), document.id(), expenseCategory.id(), txnDate, amount);

		ExpenseResponse draft = findExpenseByVendor(client.id(), "Keells Super");
		assertThat(draft.status()).isEqualTo("DRAFT");

		ExpenseResponse approved = approveExpense(client.id(), draft.id());
		assertThat(approved.status()).isEqualTo("APPROVED");

		BankAccountResponse account = createBankAccount(client.id());
		importBankCsv(client.id(), account.id(), txnDate, amount);
		List<BankTransactionResponse> imported = listBankTransactions(client.id(), account.id());
		assertThat(imported).hasSize(1);
		BankTransactionResponse bankTxn = imported.get(0);
		assertThat(bankTxn.clientId()).isEqualTo(client.id());
		assertThat(bankTxn.bankAccountId()).isEqualTo(account.id());
		assertThat(bankTxn.description()).isEqualTo("KEELLS SUPER");
		assertThat(bankTxn.matchStatus()).isNotEqualTo(com.finance.platform.finance.domain.model.BankTransaction.MatchStatus.MATCHED);

		confirmReconciliation(client.id(), bankTxn.id(), approved.id());

		PeriodResponse period = getOrCreatePeriod(client.id(), txnDate.getYear(), txnDate.getMonthValue());
		PeriodReadinessResponse readiness = getReadiness(client.id(), period.id());
		assertThat(readiness.ready()).isTrue();
		assertThat(readiness.blockers()).isEmpty();

		PeriodResponse closed = closePeriod(client.id(), period.id());
		assertThat(closed.status()).isEqualTo(AccountingPeriod.PeriodStatus.CLOSED);

		JsonNode report = profitAndLoss(client.id(), txnDate.withDayOfMonth(1), txnDate.withDayOfMonth(txnDate.lengthOfMonth()));
		assertThat(report.get("totalExpenses").decimalValue()).isGreaterThanOrEqualTo(amount);
		assertThat(report.get("hasApprovedData").asBoolean()).isTrue();
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

	private DocumentResponse uploadDocument(UUID clientId) throws Exception {
		byte[] png = new byte[]{
				(byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A,
				0, 0, 0, 0, 0, 0, 0, 0
		};
		MockMultipartFile file = new MockMultipartFile("file", "receipt.png", "image/png", png);
		MvcResult result = mockMvc.perform(multipart("/api/v1/clients/" + clientId + "/documents")
						.file(file)
						.param("documentType", "RECEIPT")
						.param("description", "Keells supplies")
						.header(HttpHeaders.AUTHORIZATION, IntegrationTestSupport.bearer(admin.token())))
				.andExpect(status().isCreated())
				.andReturn();
		return support.read(result, DocumentResponse.class);
	}

	private void waitForDocumentStatus(UUID clientId, UUID documentId, String expectedStatus) throws Exception {
		for (int attempt = 0; attempt < 50; attempt++) {
			MvcResult result = mockMvc.perform(get("/api/v1/clients/" + clientId + "/documents/" + documentId)
							.header(HttpHeaders.AUTHORIZATION, IntegrationTestSupport.bearer(admin.token())))
					.andExpect(status().isOk())
					.andReturn();
			DocumentResponse document = support.read(result, DocumentResponse.class);
			if (expectedStatus.equals(document.status())) {
				return;
			}
			Thread.sleep(200);
		}
		throw new AssertionError("Document " + documentId + " did not reach status " + expectedStatus);
	}

	private void createDraftFromDocument(
			UUID clientId,
			UUID documentId,
			UUID categoryId,
			LocalDate txnDate,
			BigDecimal amount
	) throws Exception {
		mockMvc.perform(post("/api/v1/clients/" + clientId + "/documents/" + documentId + "/transactions")
						.header(HttpHeaders.AUTHORIZATION, IntegrationTestSupport.bearer(admin.token()))
						.contentType(MediaType.APPLICATION_JSON)
						.content(objectMapper.writeValueAsString(new ModifySuggestionRequest(
								"EXPENSE",
								txnDate,
								categoryId,
								amount,
								"LKR",
								"Keells Super",
								"Golden path supplies",
								null,
								"GP-REF-001",
								null
						))))
				.andExpect(status().isOk());
	}

	private ExpenseResponse findExpenseByVendor(UUID clientId, String vendor) throws Exception {
		MvcResult result = mockMvc.perform(get("/api/v1/clients/" + clientId + "/expenses")
						.param("size", "50")
						.header(HttpHeaders.AUTHORIZATION, IntegrationTestSupport.bearer(admin.token())))
				.andExpect(status().isOk())
				.andReturn();
		JsonNode page = objectMapper.readTree(result.getResponse().getContentAsString());
		for (JsonNode row : page.get("content")) {
			if (vendor.equals(row.get("vendorName").asText())) {
				return objectMapper.treeToValue(row, ExpenseResponse.class);
			}
		}
		throw new AssertionError("Expense not found for vendor: " + vendor);
	}

	private ExpenseResponse approveExpense(UUID clientId, UUID expenseId) throws Exception {
		MvcResult result = mockMvc.perform(post("/api/v1/clients/" + clientId + "/expenses/" + expenseId + "/approve")
						.header(HttpHeaders.AUTHORIZATION, IntegrationTestSupport.bearer(admin.token()))
						.header(IDEMPOTENCY, UUID.randomUUID().toString()))
				.andExpect(status().isOk())
				.andReturn();
		return support.read(result, ExpenseResponse.class);
	}

	private BankAccountResponse createBankAccount(UUID clientId) throws Exception {
		MvcResult result = mockMvc.perform(post("/api/v1/clients/" + clientId + "/bank/accounts")
						.header(HttpHeaders.AUTHORIZATION, IntegrationTestSupport.bearer(admin.token()))
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{
								  "bankName": "Test Bank",
								  "accountName": "Operating",
								  "maskedAccountNumber": "****1234",
								  "currency": "LKR"
								}
								"""))
				.andExpect(status().isCreated())
				.andReturn();
		return support.read(result, BankAccountResponse.class);
	}

	private void importBankCsv(UUID clientId, UUID bankAccountId, LocalDate txnDate, BigDecimal amount) throws Exception {
		String csv = "Date,Description,Reference,Debit,Credit,Balance\n"
				+ txnDate + ",KEELLS SUPER,REF1001," + amount + ",,100000.00\n";
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

	private void confirmReconciliation(UUID clientId, UUID bankTransactionId, UUID expenseId) throws Exception {
		mockMvc.perform(post("/api/v1/clients/" + clientId + "/bank/transactions/" + bankTransactionId + "/confirm")
						.header(HttpHeaders.AUTHORIZATION, IntegrationTestSupport.bearer(admin.token()))
						.header(IDEMPOTENCY, UUID.randomUUID().toString())
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

	private PeriodReadinessResponse getReadiness(UUID clientId, UUID periodId) throws Exception {
		MvcResult result = mockMvc.perform(get("/api/v1/clients/" + clientId + "/periods/" + periodId + "/readiness")
						.header(HttpHeaders.AUTHORIZATION, IntegrationTestSupport.bearer(admin.token())))
				.andExpect(status().isOk())
				.andReturn();
		return support.read(result, PeriodReadinessResponse.class);
	}

	private PeriodResponse closePeriod(UUID clientId, UUID periodId) throws Exception {
		MvcResult result = mockMvc.perform(post("/api/v1/clients/" + clientId + "/periods/" + periodId + "/close")
						.header(HttpHeaders.AUTHORIZATION, IntegrationTestSupport.bearer(admin.token()))
						.header(IDEMPOTENCY, UUID.randomUUID().toString())
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"closeNote\":\"Golden path close\"}"))
				.andExpect(status().isOk())
				.andReturn();
		return support.read(result, PeriodResponse.class);
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
}
