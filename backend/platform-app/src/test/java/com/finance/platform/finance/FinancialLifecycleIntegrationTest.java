package com.finance.platform.finance;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.finance.platform.auth.application.dto.RegisterRequest;
import com.finance.platform.auth.application.dto.RegisterResponse;
import com.finance.platform.finance.application.dto.CategoryResponse;
import com.finance.platform.finance.application.dto.ClientResponse;
import com.finance.platform.finance.application.dto.CreateExpenseRequest;
import com.finance.platform.finance.application.dto.ExpenseResponse;
import com.finance.platform.support.AbstractPostgresIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class FinancialLifecycleIntegrationTest extends AbstractPostgresIntegrationTest {

	private static final String PASSWORD = "password1";

	@Autowired
	private ObjectMapper objectMapper;

	@Test
	void expenseDraftApproveVoid_lifecycleEnforced() throws Exception {
		Session admin = registerFirmAdmin("Lifecycle Firm");
		ClientResponse client = createClient(admin.token(), "Lifecycle Client");
		CategoryResponse category = createCategory(admin.token(), "OFFICE", "Office", "EXPENSE");

		ExpenseResponse draft = createExpense(admin.token(), client.id(), category.id(), "100.00", "Draft vendor");
		assertThat(draft.status()).isEqualTo("DRAFT");

		ExpenseResponse approved = approveExpense(admin.token(), client.id(), draft.id());
		assertThat(approved.status()).isEqualTo("APPROVED");

		mockMvc.perform(put("/api/v1/clients/" + client.id() + "/expenses/" + draft.id())
						.header(HttpHeaders.AUTHORIZATION, bearer(admin.token()))
						.contentType(MediaType.APPLICATION_JSON)
						.content(objectMapper.writeValueAsString(new CreateExpenseRequest(
								LocalDate.now(),
								category.id(),
								new BigDecimal("120.00"),
								"LKR",
								"Updated vendor",
								null,
								null,
								null
						))))
				.andExpect(status().isUnprocessableEntity());

		ExpenseResponse voided = voidExpense(admin.token(), client.id(), draft.id(), "Correction");
		assertThat(voided.status()).isEqualTo("VOID");

		mockMvc.perform(post("/api/v1/clients/" + client.id() + "/expenses/" + draft.id() + "/approve")
						.header(HttpHeaders.AUTHORIZATION, bearer(admin.token())))
				.andExpect(status().isUnprocessableEntity());
	}

	@Test
	void profitAndLoss_countsApprovedOnly() throws Exception {
		Session admin = registerFirmAdmin("Reporting Firm");
		ClientResponse client = createClient(admin.token(), "Reporting Client");
		CategoryResponse expenseCategory = createCategory(admin.token(), "OPS", "Operations", "EXPENSE");
		CategoryResponse incomeCategory = createCategory(admin.token(), "SALES", "Sales", "INCOME");

		approveExpense(admin.token(), client.id(),
				createExpense(admin.token(), client.id(), expenseCategory.id(), "40000.00", "Approved expense").id());
		createExpense(admin.token(), client.id(), expenseCategory.id(), "20000.00", "Draft expense");
		voidExpense(admin.token(), client.id(),
				approveExpense(admin.token(), client.id(),
						createExpense(admin.token(), client.id(), expenseCategory.id(), "10000.00", "Void expense").id()).id(),
				"Test void");

		var incomeDraft = createIncome(admin.token(), client.id(), incomeCategory.id(), "50000.00");
		approveIncome(admin.token(), client.id(), incomeDraft.id());
		approveIncome(admin.token(), client.id(),
				createIncome(admin.token(), client.id(), incomeCategory.id(), "50000.00").id());

		MvcResult report = mockMvc.perform(get("/api/v1/clients/" + client.id() + "/reports/profit-and-loss")
						.param("from", LocalDate.now().withDayOfMonth(1).toString())
						.param("to", LocalDate.now().toString())
						.header(HttpHeaders.AUTHORIZATION, bearer(admin.token())))
				.andExpect(status().isOk())
				.andReturn();

		var body = objectMapper.readTree(report.getResponse().getContentAsString());
		assertThat(body.get("totalIncome").decimalValue()).isEqualByComparingTo("100000.00");
		assertThat(body.get("totalExpenses").decimalValue()).isEqualByComparingTo("40000.00");
		assertThat(body.get("netResult").decimalValue()).isEqualByComparingTo("60000.00");
	}

	private Session registerFirmAdmin(String firmNamePrefix) throws Exception {
		String suffix = UUID.randomUUID().toString().substring(0, 8);
		String email = "admin-" + suffix + "@example.com";
		MvcResult result = mockMvc.perform(post("/api/v1/auth/register")
						.contentType(MediaType.APPLICATION_JSON)
						.content(objectMapper.writeValueAsString(new RegisterRequest(
								firmNamePrefix + " " + suffix,
								null,
								email,
								PASSWORD,
								"Admin " + suffix
						))))
				.andExpect(status().isCreated())
				.andReturn();
		RegisterResponse registered = objectMapper.readValue(result.getResponse().getContentAsString(), RegisterResponse.class);
		return new Session(registered.firmId(), login(email));
	}

	private String login(String email) throws Exception {
		MvcResult result = mockMvc.perform(post("/api/v1/auth/login")
						.contentType(MediaType.APPLICATION_JSON)
						.content(objectMapper.writeValueAsString(
								new com.finance.platform.auth.application.dto.LoginRequest(email, PASSWORD))))
				.andExpect(status().isOk())
				.andReturn();
		return objectMapper.readTree(result.getResponse().getContentAsString()).get("accessToken").asText();
	}

	private ClientResponse createClient(String token, String name) throws Exception {
		MvcResult result = mockMvc.perform(post("/api/v1/clients")
						.header(HttpHeaders.AUTHORIZATION, bearer(token))
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"name\":\"" + name + "\"}"))
				.andExpect(status().isCreated())
				.andReturn();
		return objectMapper.readValue(result.getResponse().getContentAsString(), ClientResponse.class);
	}

	private CategoryResponse createCategory(String token, String code, String name, String type) throws Exception {
		MvcResult result = mockMvc.perform(post("/api/v1/categories")
						.header(HttpHeaders.AUTHORIZATION, bearer(token))
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"code":"%s","name":"%s","categoryType":"%s"}
								""".formatted(code, name, type)))
				.andExpect(status().isCreated())
				.andReturn();
		return objectMapper.readValue(result.getResponse().getContentAsString(), CategoryResponse.class);
	}

	private ExpenseResponse createExpense(String token, UUID clientId, UUID categoryId, String amount, String vendor)
			throws Exception {
		MvcResult result = mockMvc.perform(post("/api/v1/clients/" + clientId + "/expenses")
						.header(HttpHeaders.AUTHORIZATION, bearer(token))
						.contentType(MediaType.APPLICATION_JSON)
						.content(objectMapper.writeValueAsString(new CreateExpenseRequest(
								LocalDate.now(),
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
		return objectMapper.readValue(result.getResponse().getContentAsString(), ExpenseResponse.class);
	}

	private ExpenseResponse approveExpense(String token, UUID clientId, UUID expenseId) throws Exception {
		MvcResult result = mockMvc.perform(post("/api/v1/clients/" + clientId + "/expenses/" + expenseId + "/approve")
						.header(HttpHeaders.AUTHORIZATION, bearer(token)))
				.andExpect(status().isOk())
				.andReturn();
		return objectMapper.readValue(result.getResponse().getContentAsString(), ExpenseResponse.class);
	}

	private ExpenseResponse voidExpense(String token, UUID clientId, UUID expenseId, String reason) throws Exception {
		MvcResult result = mockMvc.perform(post("/api/v1/clients/" + clientId + "/expenses/" + expenseId + "/void")
						.header(HttpHeaders.AUTHORIZATION, bearer(token))
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"reason\":\"" + reason + "\"}"))
				.andExpect(status().isOk())
				.andReturn();
		return objectMapper.readValue(result.getResponse().getContentAsString(), ExpenseResponse.class);
	}

	private com.finance.platform.finance.application.dto.IncomeResponse createIncome(
			String token, UUID clientId, UUID categoryId, String amount) throws Exception {
		MvcResult result = mockMvc.perform(post("/api/v1/clients/" + clientId + "/income")
						.header(HttpHeaders.AUTHORIZATION, bearer(token))
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{
								  "transactionDate":"%s",
								  "categoryId":"%s",
								  "amount":%s,
								  "currencyCode":"LKR",
								  "customerName":"Customer",
								  "paymentMethod":"BANK_TRANSFER"
								}
								""".formatted(LocalDate.now(), categoryId, amount)))
				.andExpect(status().isCreated())
				.andReturn();
		return objectMapper.readValue(result.getResponse().getContentAsString(),
				com.finance.platform.finance.application.dto.IncomeResponse.class);
	}

	private com.finance.platform.finance.application.dto.IncomeResponse approveIncome(
			String token, UUID clientId, UUID incomeId) throws Exception {
		MvcResult result = mockMvc.perform(post("/api/v1/clients/" + clientId + "/income/" + incomeId + "/approve")
						.header(HttpHeaders.AUTHORIZATION, bearer(token)))
				.andExpect(status().isOk())
				.andReturn();
		return objectMapper.readValue(result.getResponse().getContentAsString(),
				com.finance.platform.finance.application.dto.IncomeResponse.class);
	}

	private static String bearer(String token) {
		return "Bearer " + token;
	}

	private record Session(java.util.UUID firmId, String token) {
	}
}
