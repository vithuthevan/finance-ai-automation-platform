package com.finance.platform.controller.finance;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.finance.platform.auth.application.dto.CreateUserRequest;
import com.finance.platform.auth.application.dto.LoginRequest;
import com.finance.platform.auth.application.dto.LoginResponse;
import com.finance.platform.auth.application.dto.RegisterRequest;
import com.finance.platform.auth.application.dto.RegisterResponse;
import com.finance.platform.auth.domain.model.Role;
import com.finance.platform.auth.infrastructure.persistence.RoleJpaRepository;
import com.finance.platform.core.audit.AuditAction;
import com.finance.platform.core.audit.AuditLog;
import com.finance.platform.core.audit.AuditLogJpaRepository;
import com.finance.platform.core.audit.AuditResourceType;
import com.finance.platform.finance.application.dto.CategoryResponse;
import com.finance.platform.finance.application.dto.ClientResponse;
import com.finance.platform.finance.application.dto.CreateExpenseRequest;
import com.finance.platform.finance.application.dto.ExpenseResponse;
import com.finance.platform.finance.domain.model.Category;
import com.finance.platform.finance.infrastructure.persistence.CategoryJpaRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import com.finance.platform.support.AbstractPostgresIntegrationTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class CategoryCreationIntegrationTest extends AbstractPostgresIntegrationTest {

	private static final String PASSWORD = "password1";

	@Autowired
	private ObjectMapper objectMapper;

	@Autowired
	private RoleJpaRepository roleRepository;

	@Autowired
	private CategoryJpaRepository categoryRepository;

	@Autowired
	private AuditLogJpaRepository auditLogRepository;

	@BeforeEach
	void seedRoles() {
		if (roleRepository.count() > 0) {
			return;
		}
		roleRepository.save(Role.builder().id((short) 1).code(Role.RoleCode.ADMIN).name("Firm Administrator").build());
		roleRepository.save(Role.builder().id((short) 2).code(Role.RoleCode.ACCOUNTANT).name("Accountant").build());
		roleRepository.save(Role.builder().id((short) 3).code(Role.RoleCode.AUDITOR).name("Auditor").build());
		roleRepository.save(Role.builder().id((short) 4).code(Role.RoleCode.BUSINESS_OWNER).name("Business Owner").build());
	}

	@Test
	void adminCreateCategory_persistsFirmWideDefaults_andWritesAudit() throws Exception {
		Session admin = registerFirmAdmin("Cat Firm");

		MvcResult result = mockMvc.perform(post("/api/v1/categories")
						.header(HttpHeaders.AUTHORIZATION, bearer(admin.token()))
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{
								  "code": "TRAVEL",
								  "name": "Travel",
								  "categoryType": "EXPENSE"
								}
								"""))
				.andExpect(status().isCreated())
				.andReturn();

		CategoryResponse body = read(result, CategoryResponse.class);
		assertThat(body.id()).isNotNull();
		assertThat(body.firmId()).isEqualTo(admin.firmId());
		assertThat(body.code()).isEqualTo("TRAVEL");
		assertThat(body.name()).isEqualTo("Travel");
		assertThat(body.categoryType()).isEqualTo(Category.CategoryType.EXPENSE);
		assertThat(body.active()).isTrue();

		Category stored = categoryRepository.findById(body.id()).orElseThrow();
		assertThat(stored.getFirmId()).isEqualTo(admin.firmId());
		assertThat(stored.getCode()).isEqualTo("TRAVEL");
		assertThat(stored.getName()).isEqualTo("Travel");
		assertThat(stored.getCategoryType()).isEqualTo(Category.CategoryType.EXPENSE);
		assertThat(stored.getClient()).isNull();
		assertThat(stored.getParent()).isNull();
		assertThat(stored.isSystem()).isFalse();
		assertThat(stored.isActive()).isTrue();
		assertThat(stored.getDeletedAt()).isNull();

		assertThat(categoryCreatedAudit(body.id(), admin.firmId())).isPresent();
	}

	@Test
	void sameCode_allowedAcrossFirms_listsAreIsolated() throws Exception {
		Session firmA = registerFirmAdmin("Iso Firm A");
		Session firmB = registerFirmAdmin("Iso Firm B");

		CategoryResponse categoryA = createCategory(firmA.token(), "TRAVEL", "Travel", "EXPENSE");
		CategoryResponse categoryB = createCategory(firmB.token(), "TRAVEL", "Travel", "EXPENSE");

		assertThat(categoryA.firmId()).isEqualTo(firmA.firmId());
		assertThat(categoryB.firmId()).isEqualTo(firmB.firmId());
		assertThat(categoryA.id()).isNotEqualTo(categoryB.id());

		List<UUID> listedA = listCategoryIds(firmA.token());
		List<UUID> listedB = listCategoryIds(firmB.token());

		assertThat(listedA).contains(categoryA.id()).doesNotContain(categoryB.id());
		assertThat(listedB).contains(categoryB.id()).doesNotContain(categoryA.id());
	}

	@Test
	void extraFirmIdInBody_isIgnored_categoryBelongsToAuthenticatedFirm() throws Exception {
		Session firmA = registerFirmAdmin("Json Cat A");
		Session firmB = registerFirmAdmin("Json Cat B");

		MvcResult result = mockMvc.perform(post("/api/v1/categories")
						.header(HttpHeaders.AUTHORIZATION, bearer(firmA.token()))
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{
								  "code": "OFFICE",
								  "name": "Office",
								  "categoryType": "EXPENSE",
								  "firmId": "%s"
								}
								""".formatted(firmB.firmId())))
				.andExpect(status().isCreated())
				.andReturn();

		CategoryResponse body = read(result, CategoryResponse.class);
		assertThat(body.firmId()).isEqualTo(firmA.firmId());
		assertThat(body.firmId()).isNotEqualTo(firmB.firmId());

		Category stored = categoryRepository.findById(body.id()).orElseThrow();
		assertThat(stored.getFirmId()).isEqualTo(firmA.firmId());
		assertThat(categoryRepository.findByIdAndFirmId(body.id(), firmB.firmId())).isEmpty();
	}

	@Test
	void duplicateCode_sameFirm_conflict_otherFirm_allowed() throws Exception {
		Session firmA = registerFirmAdmin("Dup Cat A");
		Session firmB = registerFirmAdmin("Dup Cat B");

		createCategory(firmA.token(), "TRAVEL", "Travel", "EXPENSE");
		createCategory(firmB.token(), "TRAVEL", "Travel", "EXPENSE");

		mockMvc.perform(post("/api/v1/categories")
						.header(HttpHeaders.AUTHORIZATION, bearer(firmA.token()))
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"code":"TRAVEL","name":"Travel again","categoryType":"EXPENSE"}
								"""))
				.andExpect(status().isConflict());

		assertThat(categoryRepository.findAllByFirmIdAndDeletedAtIsNull(firmA.firmId()))
				.extracting(Category::getCode)
				.contains("TRAVEL");
		assertThat(categoryRepository.findAllByFirmIdAndDeletedAtIsNull(firmA.firmId()).stream()
				.filter(category -> "TRAVEL".equals(category.getCode())).count()).isEqualTo(1);
		assertThat(categoryRepository.findAllByFirmIdAndDeletedAtIsNull(firmB.firmId()))
				.extracting(Category::getCode)
				.contains("TRAVEL");
	}

	@ParameterizedTest
	@ValueSource(strings = {"ACCOUNTANT", "AUDITOR", "BUSINESS_OWNER"})
	void nonAdminCannotCreateCategory(String role) throws Exception {
		Session admin = registerFirmAdmin("Staff Cat " + role);
		long before = categoryCount(admin.firmId());

		String token = createStaffAndLogin(admin, role);

		mockMvc.perform(post("/api/v1/categories")
						.header(HttpHeaders.AUTHORIZATION, bearer(token))
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"code":"BLOCKED","name":"Blocked","categoryType":"EXPENSE"}
								"""))
				.andExpect(status().isForbidden());

		assertThat(categoryCount(admin.firmId())).isEqualTo(before);
	}

	@Test
	void unauthenticatedCreateAndList_return401() throws Exception {
		long before = categoryRepository.count();

		mockMvc.perform(post("/api/v1/categories")
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"code":"ANON","name":"Anon","categoryType":"EXPENSE"}
								"""))
				.andExpect(status().isUnauthorized());

		mockMvc.perform(get("/api/v1/categories"))
				.andExpect(status().isUnauthorized());

		assertThat(categoryRepository.count()).isEqualTo(before);
	}

	@ParameterizedTest
	@ValueSource(strings = {"ADMIN", "ACCOUNTANT", "AUDITOR", "BUSINESS_OWNER"})
	void anyAuthenticatedRoleCanListFirmCategories(String role) throws Exception {
		Session admin = registerFirmAdmin("List Firm " + role);
		CategoryResponse created = createCategory(admin.token(), "MEALS", "Meals", "EXPENSE");

		String token = "ADMIN".equals(role) ? admin.token() : createStaffAndLogin(admin, role);

		MvcResult result = mockMvc.perform(get("/api/v1/categories")
						.header(HttpHeaders.AUTHORIZATION, bearer(token)))
				.andExpect(status().isOk())
				.andReturn();

		List<CategoryResponse> listed = Arrays.asList(read(result, CategoryResponse[].class));
		assertThat(listed).isNotEmpty();
		assertThat(listed).allMatch(category -> admin.firmId().equals(category.firmId()));
		assertThat(listed).extracting(CategoryResponse::id).contains(created.id());
	}

	@ParameterizedTest
	@MethodSource("invalidCategoryBodies")
	void invalidCreateCategory_returns400_andPersistsNothing(String body) throws Exception {
		Session admin = registerFirmAdmin("Val Cat");
		long before = categoryCount(admin.firmId());

		mockMvc.perform(post("/api/v1/categories")
						.header(HttpHeaders.AUTHORIZATION, bearer(admin.token()))
						.contentType(MediaType.APPLICATION_JSON)
						.content(body))
				.andExpect(status().isBadRequest());

		assertThat(categoryCount(admin.firmId())).isEqualTo(before);
	}

	static Stream<Arguments> invalidCategoryBodies() {
		return Stream.of(
				Arguments.of("""
						{"name":"Travel","categoryType":"EXPENSE"}
						"""),
				Arguments.of("""
						{"code":"   ","name":"Travel","categoryType":"EXPENSE"}
						"""),
				Arguments.of("""
						{"code":"%s","name":"Travel","categoryType":"EXPENSE"}
						""".formatted("C".repeat(31))),
				Arguments.of("""
						{"code":"TRAVEL","categoryType":"EXPENSE"}
						"""),
				Arguments.of("""
						{"code":"TRAVEL","name":"   ","categoryType":"EXPENSE"}
						"""),
				Arguments.of("""
						{"code":"TRAVEL","name":"%s","categoryType":"EXPENSE"}
						""".formatted("N".repeat(101))),
				Arguments.of("""
						{"code":"TRAVEL","name":"Travel"}
						"""),
				Arguments.of("""
						{"code":"TRAVEL","name":"Travel","categoryType":"NOT_A_TYPE"}
						""")
		);
	}

	@Test
	void createdCategory_canBeUsedToCreateExpense() throws Exception {
		Session admin = registerFirmAdmin("Expense Firm");
		ClientResponse client = createClient(admin.token(), "SME Client");
		CategoryResponse category = createCategory(admin.token(), "TRAVEL", "Travel", "EXPENSE");

		MvcResult result = mockMvc.perform(post("/api/v1/clients/{clientId}/expenses", client.id())
						.header(HttpHeaders.AUTHORIZATION, bearer(admin.token()))
						.contentType(MediaType.APPLICATION_JSON)
						.content(objectMapper.writeValueAsString(new CreateExpenseRequest(
								LocalDate.now().minusDays(1),
								category.id(),
								new BigDecimal("1500.00"),
								"LKR",
								"Airline",
								"Flight",
								null,
								null
						))))
				.andExpect(status().isCreated())
				.andReturn();

		ExpenseResponse expense = read(result, ExpenseResponse.class);
		assertThat(expense.id()).isNotNull();
		assertThat(expense.clientId()).isEqualTo(client.id());
		assertThat(expense.categoryId()).isEqualTo(category.id());
		assertThat(expense.status()).isEqualTo("DRAFT");
	}

	@Test
	void listCategories_excludesSoftDeletedRows() throws Exception {
		Session admin = registerFirmAdmin("Soft Delete Firm");
		CategoryResponse visible = createCategory(admin.token(), "VISIBLE", "Visible", "EXPENSE");
		CategoryResponse hidden = createCategory(admin.token(), "HIDDEN", "Hidden", "EXPENSE");

		Category stored = categoryRepository.findById(hidden.id()).orElseThrow();
		stored.setDeletedAt(Instant.now());
		categoryRepository.save(stored);

		List<UUID> listed = listCategoryIds(admin.token());
		assertThat(listed).contains(visible.id()).doesNotContain(hidden.id());
	}

	private Session registerFirmAdmin(String firmNamePrefix) throws Exception {
		String suffix = uniqueSuffix();
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
		RegisterResponse registered = read(result, RegisterResponse.class);
		return new Session(registered.firmId(), registered.userId(), email, login(email));
	}

	private String createStaffAndLogin(Session admin, String role) throws Exception {
		String suffix = uniqueSuffix();
		String email = role.toLowerCase() + "-" + suffix + "@example.com";
		mockMvc.perform(post("/api/v1/users")
						.header(HttpHeaders.AUTHORIZATION, bearer(admin.token()))
						.contentType(MediaType.APPLICATION_JSON)
						.content(objectMapper.writeValueAsString(new CreateUserRequest(
								email,
								PASSWORD,
								role + " User",
								role,
								List.of()
						))))
				.andExpect(status().isCreated());
		return login(email);
	}

	private String login(String email) throws Exception {
		MvcResult result = mockMvc.perform(post("/api/v1/auth/login")
						.contentType(MediaType.APPLICATION_JSON)
						.content(objectMapper.writeValueAsString(new LoginRequest(email, PASSWORD))))
				.andExpect(status().isOk())
				.andReturn();
		return read(result, LoginResponse.class).accessToken();
	}

	private CategoryResponse createCategory(String token, String code, String name, String categoryType)
			throws Exception {
		MvcResult result = mockMvc.perform(post("/api/v1/categories")
						.header(HttpHeaders.AUTHORIZATION, bearer(token))
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"code":"%s","name":"%s","categoryType":"%s"}
								""".formatted(code, name, categoryType)))
				.andExpect(status().isCreated())
				.andReturn();
		return read(result, CategoryResponse.class);
	}

	private ClientResponse createClient(String token, String name) throws Exception {
		MvcResult result = mockMvc.perform(post("/api/v1/clients")
						.header(HttpHeaders.AUTHORIZATION, bearer(token))
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"name":"%s"}
								""".formatted(name)))
				.andExpect(status().isCreated())
				.andReturn();
		return read(result, ClientResponse.class);
	}

	private List<UUID> listCategoryIds(String token) throws Exception {
		MvcResult result = mockMvc.perform(get("/api/v1/categories")
						.header(HttpHeaders.AUTHORIZATION, bearer(token)))
				.andExpect(status().isOk())
				.andReturn();
		return Arrays.stream(read(result, CategoryResponse[].class))
				.map(CategoryResponse::id)
				.toList();
	}

	private long categoryCount(UUID firmId) {
		return categoryRepository.findAllByFirmIdAndDeletedAtIsNull(firmId).size();
	}

	private Optional<AuditLog> categoryCreatedAudit(UUID categoryId, UUID firmId) {
		return auditLogRepository.findAll().stream()
				.filter(log -> AuditAction.CATEGORY_CREATED.name().equals(log.getAction()))
				.filter(log -> AuditResourceType.CATEGORY.name().equals(log.getResourceType()))
				.filter(log -> categoryId.equals(log.getResourceId()))
				.filter(log -> firmId.equals(log.getFirmId()))
				.findFirst();
	}

	private <T> T read(MvcResult result, Class<T> type) throws Exception {
		return objectMapper.readValue(result.getResponse().getContentAsString(), type);
	}

	private static String bearer(String token) {
		return "Bearer " + token;
	}

	private static String uniqueSuffix() {
		return UUID.randomUUID().toString().substring(0, 8);
	}

	private record Session(UUID firmId, UUID userId, String email, String token) {
	}
}
