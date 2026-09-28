package com.finance.platform.security;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.finance.platform.core.notification.Notification;
import com.finance.platform.finance.application.dto.BankAccountResponse;
import com.finance.platform.finance.application.dto.BankTransactionResponse;
import com.finance.platform.finance.application.dto.CategoryResponse;
import com.finance.platform.finance.application.dto.ClientResponse;
import com.finance.platform.finance.application.dto.CreateExpenseRequest;
import com.finance.platform.finance.application.dto.DocumentResponse;
import com.finance.platform.finance.application.dto.ExpenseResponse;
import com.finance.platform.finance.application.dto.IncomeRequest;
import com.finance.platform.finance.application.dto.IncomeResponse;
import com.finance.platform.finance.application.dto.PeriodResponse;
import com.finance.platform.finance.domain.model.PaymentMethod;
import com.finance.platform.support.IntegrationTestSupport;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Seeds a rich victim-tenant dataset through the public HTTP API for isolation tests.
 */
public final class TenantIsolationDataSeeder {

	private TenantIsolationDataSeeder() {
	}

	public static TenantIsolationVictimResources seedVictimFirm(
			MockMvc mockMvc,
			IntegrationTestSupport support,
			ObjectMapper objectMapper,
			String firmLabel
	) throws Exception {
		IntegrationTestSupport.Session victim = support.registerFirmAdmin(firmLabel);
		ClientResponse client = createClient(mockMvc, support, victim.token(), "Victim Client");
		CategoryResponse expenseCategory = createCategory(
				mockMvc, support, victim.token(), "ISO-EXP", "Isolation Expense", "EXPENSE");
		CategoryResponse incomeCategory = createCategory(
				mockMvc, support, victim.token(), "ISO-INC", "Isolation Income", "INCOME");
		ExpenseResponse expense = createExpense(
				mockMvc, support, objectMapper, victim.token(), client.id(), expenseCategory.id());
		IncomeResponse income = createIncome(
				mockMvc, support, objectMapper, victim.token(), client.id(), incomeCategory.id());
		DocumentResponse document = uploadDocument(mockMvc, support, victim.token(), client.id());
		BankAccountResponse bankAccount = createBankAccount(mockMvc, support, victim.token(), client.id());
		importBankCsv(mockMvc, victim.token(), client.id(), bankAccount.id());
		List<BankTransactionResponse> bankTxns = listBankTransactions(mockMvc, support, objectMapper, victim.token(), client.id(), bankAccount.id());
		PeriodResponse period = getOrCreatePeriod(mockMvc, support, victim.token(), client.id(), LocalDate.now());
		UUID arCustomerId = createArCustomer(mockMvc, objectMapper, victim.token());
		UUID arInvoiceId = createAndIssueArInvoice(mockMvc, objectMapper, victim.token(), arCustomerId);
		UUID arPaymentId = recordArPayment(mockMvc, objectMapper, victim.token(), arCustomerId, BigDecimal.TEN);
		UUID salesInvoiceId = createSalesInvoiceDraft(mockMvc, objectMapper, victim.token(), arCustomerId);

		return new TenantIsolationVictimResources(
				victim,
				client.id(),
				expenseCategory.id(),
				incomeCategory.id(),
				expense.id(),
				income.id(),
				document.id(),
				bankAccount.id(),
				bankTxns.get(0).id(),
				period.id(),
				arCustomerId,
				arInvoiceId,
				arPaymentId,
				salesInvoiceId
		);
	}

	public static Notification victimNotification(TenantIsolationVictimResources victim) {
		return Notification.builder()
				.firmId(victim.victim().firmId())
				.userId(victim.victim().userId())
				.clientId(victim.clientId())
				.type("TENANT_ISO_TEST")
				.title("Victim notification")
				.message("Must not be readable by other firms")
				.build();
	}

	private static ClientResponse createClient(
			MockMvc mockMvc, IntegrationTestSupport support, String token, String name
	) throws Exception {
		MvcResult result = mockMvc.perform(post("/api/v1/clients")
						.header(HttpHeaders.AUTHORIZATION, IntegrationTestSupport.bearer(token))
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"name\":\"" + name + "\"}"))
				.andExpect(status().isCreated())
				.andReturn();
		return support.read(result, ClientResponse.class);
	}

	private static CategoryResponse createCategory(
			MockMvc mockMvc, IntegrationTestSupport support, String token, String code, String name, String type
	) throws Exception {
		MvcResult result = mockMvc.perform(post("/api/v1/categories")
						.header(HttpHeaders.AUTHORIZATION, IntegrationTestSupport.bearer(token))
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"code":"%s","name":"%s","categoryType":"%s"}
								""".formatted(code, name, type)))
				.andExpect(status().isCreated())
				.andReturn();
		return support.read(result, CategoryResponse.class);
	}

	private static ExpenseResponse createExpense(
			MockMvc mockMvc,
			IntegrationTestSupport support,
			ObjectMapper objectMapper,
			String token,
			UUID clientId,
			UUID categoryId
	) throws Exception {
		MvcResult result = mockMvc.perform(post("/api/v1/clients/" + clientId + "/expenses")
						.header(HttpHeaders.AUTHORIZATION, IntegrationTestSupport.bearer(token))
						.header("Idempotency-Key", UUID.randomUUID().toString())
						.contentType(MediaType.APPLICATION_JSON)
						.content(objectMapper.writeValueAsString(new CreateExpenseRequest(
								LocalDate.now(),
								categoryId,
								new BigDecimal("150.00"),
								"LKR",
								"Victim Vendor",
								null,
								null,
								null
						))))
				.andExpect(status().isCreated())
				.andReturn();
		return support.read(result, ExpenseResponse.class);
	}

	private static IncomeResponse createIncome(
			MockMvc mockMvc,
			IntegrationTestSupport support,
			ObjectMapper objectMapper,
			String token,
			UUID clientId,
			UUID categoryId
	) throws Exception {
		MvcResult result = mockMvc.perform(post("/api/v1/clients/" + clientId + "/income")
						.header(HttpHeaders.AUTHORIZATION, IntegrationTestSupport.bearer(token))
						.header("Idempotency-Key", UUID.randomUUID().toString())
						.contentType(MediaType.APPLICATION_JSON)
						.content(objectMapper.writeValueAsString(new IncomeRequest(
								LocalDate.now(),
								categoryId,
								new BigDecimal("200.00"),
								"LKR",
								"Victim Customer",
								null,
								PaymentMethod.BANK_TRANSFER,
								null,
								null
						))))
				.andExpect(status().isCreated())
				.andReturn();
		return support.read(result, IncomeResponse.class);
	}

	private static DocumentResponse uploadDocument(
			MockMvc mockMvc, IntegrationTestSupport support, String token, UUID clientId
	) throws Exception {
		byte[] png = new byte[]{
				(byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A,
				0, 0, 0, 0, 0, 0, 0, 0
		};
		MockMultipartFile file = new MockMultipartFile("file", "iso.png", "image/png", png);
		MvcResult result = mockMvc.perform(multipart("/api/v1/clients/" + clientId + "/documents").file(file)
						.header(HttpHeaders.AUTHORIZATION, IntegrationTestSupport.bearer(token)))
				.andExpect(status().isCreated())
				.andReturn();
		return support.read(result, DocumentResponse.class);
	}

	private static BankAccountResponse createBankAccount(
			MockMvc mockMvc, IntegrationTestSupport support, String token, UUID clientId
	) throws Exception {
		MvcResult result = mockMvc.perform(post("/api/v1/clients/" + clientId + "/bank/accounts")
						.header(HttpHeaders.AUTHORIZATION, IntegrationTestSupport.bearer(token))
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{
								  "bankName": "Iso Bank",
								  "accountName": "Operating",
								  "maskedAccountNumber": "****9999",
								  "currency": "LKR"
								}
								"""))
				.andExpect(status().isCreated())
				.andReturn();
		return support.read(result, BankAccountResponse.class);
	}

	private static void importBankCsv(MockMvc mockMvc, String token, UUID clientId, UUID bankAccountId) throws Exception {
		LocalDate txnDate = LocalDate.now().withDayOfMonth(Math.min(15, LocalDate.now().lengthOfMonth()));
		String csv = "Date,Description,Reference,Debit,Credit,Balance\n"
				+ txnDate + ",ISO VENDOR,ISO-REF," + "75.00" + ",,1000.00\n";
		MockMultipartFile file = new MockMultipartFile(
				"file", "iso-bank.csv", "text/csv", csv.getBytes(StandardCharsets.UTF_8));
		mockMvc.perform(multipart("/api/v1/clients/" + clientId + "/bank/imports")
						.file(file)
						.param("bankAccountId", bankAccountId.toString())
						.header(HttpHeaders.AUTHORIZATION, IntegrationTestSupport.bearer(token))
						.header("Idempotency-Key", UUID.randomUUID().toString()))
				.andExpect(status().isCreated());
	}

	private static List<BankTransactionResponse> listBankTransactions(
			MockMvc mockMvc,
			IntegrationTestSupport support,
			ObjectMapper objectMapper,
			String token,
			UUID clientId,
			UUID bankAccountId
	) throws Exception {
		MvcResult result = mockMvc.perform(get("/api/v1/clients/" + clientId + "/bank/transactions")
						.param("bankAccountId", bankAccountId.toString())
						.param("size", "20")
						.header(HttpHeaders.AUTHORIZATION, IntegrationTestSupport.bearer(token)))
				.andExpect(status().isOk())
				.andReturn();
		JsonNode page = objectMapper.readTree(result.getResponse().getContentAsString());
		List<BankTransactionResponse> rows = new java.util.ArrayList<>();
		for (JsonNode row : page.get("content")) {
			rows.add(objectMapper.treeToValue(row, BankTransactionResponse.class));
		}
		if (rows.isEmpty()) {
			throw new IllegalStateException("Expected at least one bank transaction after import");
		}
		return rows;
	}

	private static PeriodResponse getOrCreatePeriod(
			MockMvc mockMvc, IntegrationTestSupport support, String token, UUID clientId, LocalDate month
	) throws Exception {
		MvcResult result = mockMvc.perform(post("/api/v1/clients/" + clientId + "/periods")
						.param("year", String.valueOf(month.getYear()))
						.param("month", String.valueOf(month.getMonthValue()))
						.header(HttpHeaders.AUTHORIZATION, IntegrationTestSupport.bearer(token)))
				.andExpect(status().isOk())
				.andReturn();
		return support.read(result, PeriodResponse.class);
	}

	private static UUID createArCustomer(MockMvc mockMvc, ObjectMapper objectMapper, String token) throws Exception {
		String body = """
				{"name":"AR Victim","email":"ar-victim@test.com","paymentTermsDays":30}
				""";
		String json = mockMvc.perform(post("/api/v1/ar/customers")
						.header(HttpHeaders.AUTHORIZATION, IntegrationTestSupport.bearer(token))
						.contentType(MediaType.APPLICATION_JSON)
						.content(body))
				.andExpect(status().isCreated())
				.andReturn().getResponse().getContentAsString();
		return UUID.fromString(objectMapper.readTree(json).get("id").asText());
	}

	private static UUID createAndIssueArInvoice(MockMvc mockMvc, ObjectMapper objectMapper, String token, UUID customerId)
			throws Exception {
		String body = """
				{"customerId":"%s","lines":[{"description":"Svc","quantity":1,"unitPrice":500}]}
				""".formatted(customerId);
		String json = mockMvc.perform(post("/api/v1/ar/invoices")
						.header(HttpHeaders.AUTHORIZATION, IntegrationTestSupport.bearer(token))
						.contentType(MediaType.APPLICATION_JSON)
						.content(body))
				.andExpect(status().isCreated())
				.andReturn().getResponse().getContentAsString();
		UUID invoiceId = UUID.fromString(objectMapper.readTree(json).get("id").asText());
		mockMvc.perform(post("/api/v1/ar/invoices/{id}/issue", invoiceId)
						.header(HttpHeaders.AUTHORIZATION, IntegrationTestSupport.bearer(token))
						.header("Idempotency-Key", UUID.randomUUID().toString()))
				.andExpect(status().isOk());
		return invoiceId;
	}

	private static UUID recordArPayment(MockMvc mockMvc, ObjectMapper objectMapper, String token, UUID customerId, BigDecimal amount)
			throws Exception {
		String body = """
				{"customerId":"%s","paymentDate":"2026-03-01","amount":%s,"reference":"ISO-PAY"}
				""".formatted(customerId, amount.toPlainString());
		String json = mockMvc.perform(post("/api/v1/ar/payments")
						.header(HttpHeaders.AUTHORIZATION, IntegrationTestSupport.bearer(token))
						.contentType(MediaType.APPLICATION_JSON)
						.content(body))
				.andExpect(status().isCreated())
				.andReturn().getResponse().getContentAsString();
		return UUID.fromString(objectMapper.readTree(json).get("id").asText());
	}

	private static UUID createSalesInvoiceDraft(MockMvc mockMvc, ObjectMapper objectMapper, String token, UUID customerId)
			throws Exception {
		String body = """
				{"customerId":"%s","lines":[{"description":"Draft line","quantity":1,"unitPrice":100}]}
				""".formatted(customerId);
		String json = mockMvc.perform(post("/api/v1/ar/invoices")
						.header(HttpHeaders.AUTHORIZATION, IntegrationTestSupport.bearer(token))
						.contentType(MediaType.APPLICATION_JSON)
						.content(body))
				.andExpect(status().isCreated())
				.andReturn().getResponse().getContentAsString();
		return UUID.fromString(objectMapper.readTree(json).get("id").asText());
	}
}
