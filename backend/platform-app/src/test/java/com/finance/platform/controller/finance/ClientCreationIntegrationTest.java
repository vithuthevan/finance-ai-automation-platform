package com.finance.platform.controller.finance;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.finance.platform.auth.application.dto.CreateUserRequest;
import com.finance.platform.auth.application.dto.LoginRequest;
import com.finance.platform.auth.application.dto.LoginResponse;
import com.finance.platform.auth.application.dto.RegisterRequest;
import com.finance.platform.auth.application.dto.RegisterResponse;
import com.finance.platform.auth.application.dto.UserResponse;
import com.finance.platform.auth.domain.model.Role;
import com.finance.platform.auth.infrastructure.persistence.RoleJpaRepository;
import com.finance.platform.auth.infrastructure.persistence.UserClientAccessJpaRepository;
import com.finance.platform.core.audit.AuditAction;
import com.finance.platform.core.audit.AuditLog;
import com.finance.platform.core.audit.AuditLogJpaRepository;
import com.finance.platform.core.audit.AuditResourceType;
import com.finance.platform.finance.application.dto.ClientResponse;
import com.finance.platform.finance.domain.model.Client;
import com.finance.platform.finance.infrastructure.persistence.ClientJpaRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import com.finance.platform.support.BaseWebIntegrationTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class ClientCreationIntegrationTest extends BaseWebIntegrationTest {

	private static final String PASSWORD = "password1";

	@Autowired
	private ObjectMapper objectMapper;

	@Autowired
	private RoleJpaRepository roleRepository;

	@Autowired
	private ClientJpaRepository clientRepository;

	@Autowired
	private AuditLogJpaRepository auditLogRepository;

	@Autowired
	private UserClientAccessJpaRepository userClientAccessRepository;

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
	void adminCreateClient_persistsUnderCallerFirm_andWritesAudit() throws Exception {
		Session admin = registerFirmAdmin("Acme Firm");

		MvcResult result = mockMvc.perform(post("/api/v1/clients")
						.header(HttpHeaders.AUTHORIZATION, bearer(admin.token()))
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{
								  "name": "Acme Trading",
								  "businessRegNo": "PV12345",
								  "contactEmail": "ops@acme.lk"
								}
								"""))
				.andExpect(status().isCreated())
				.andReturn();

		ClientResponse body = read(result, ClientResponse.class);
		assertThat(body.id()).isNotNull();
		assertThat(body.firmId()).isEqualTo(admin.firmId());
		assertThat(body.name()).isEqualTo("Acme Trading");
		assertThat(body.businessRegNo()).isEqualTo("PV12345");
		assertThat(body.contactEmail()).isEqualTo("ops@acme.lk");
		assertThat(body.active()).isTrue();

		Client stored = clientRepository.findById(body.id()).orElseThrow();
		assertThat(stored.getFirmId()).isEqualTo(admin.firmId());
		assertThat(stored.getName()).isEqualTo("Acme Trading");
		assertThat(stored.getBusinessRegNo()).isEqualTo("PV12345");
		assertThat(stored.getContactEmail()).isEqualTo("ops@acme.lk");
		assertThat(stored.isActive()).isTrue();
		assertThat(stored.getDeletedAt()).isNull();

		assertThat(clientCreatedAudit(body.id(), admin.firmId())).isPresent();
	}

	@Test
	void createdClient_belongsToAuthenticatedFirm_notAnotherFirm() throws Exception {
		Session firmA = registerFirmAdmin("Firm A");
		Session firmB = registerFirmAdmin("Firm B");

		ClientResponse created = createClient(firmA.token(), "Isolated Client", null, null);

		assertThat(created.firmId()).isEqualTo(firmA.firmId());
		assertThat(created.firmId()).isNotEqualTo(firmB.firmId());

		Client stored = clientRepository.findById(created.id()).orElseThrow();
		assertThat(stored.getFirmId()).isEqualTo(firmA.firmId());
		assertThat(clientRepository.findByIdAndFirmId(created.id(), firmB.firmId())).isEmpty();
	}

	@ParameterizedTest
	@ValueSource(strings = {"ACCOUNTANT", "AUDITOR", "BUSINESS_OWNER"})
	void nonAdminCannotCreateClient(String role) throws Exception {
		Session admin = registerFirmAdmin("Staff Firm " + role);
		long before = clientCount(admin.firmId());

		String token = createStaffAndLogin(admin, role);

		mockMvc.perform(post("/api/v1/clients")
						.header(HttpHeaders.AUTHORIZATION, bearer(token))
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"name":"Should Not Persist"}
								"""))
				.andExpect(status().isForbidden());

		assertThat(clientCount(admin.firmId())).isEqualTo(before);
	}

	@Test
	void unauthenticatedCreateClient_returns401_andPersistsNothing() throws Exception {
		long before = clientRepository.count();

		mockMvc.perform(post("/api/v1/clients")
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"name":"Anonymous Client"}
								"""))
				.andExpect(status().isUnauthorized());

		assertThat(clientRepository.count()).isEqualTo(before);
	}

	@ParameterizedTest
	@MethodSource("invalidClientBodies")
	void invalidCreateClient_returns400_andPersistsNothing(String body) throws Exception {
		Session admin = registerFirmAdmin("Validation Firm");
		long before = clientCount(admin.firmId());

		mockMvc.perform(post("/api/v1/clients")
						.header(HttpHeaders.AUTHORIZATION, bearer(admin.token()))
						.contentType(MediaType.APPLICATION_JSON)
						.content(body))
				.andExpect(status().isBadRequest());

		assertThat(clientCount(admin.firmId())).isEqualTo(before);
	}

	static Stream<Arguments> invalidClientBodies() {
		return Stream.of(
				Arguments.of("{}"),
				Arguments.of("""
						{"name":"   "}
						"""),
				Arguments.of("""
						{"name":"%s"}
						""".formatted("A".repeat(201))),
				Arguments.of("""
						{"name":"Valid Name","businessRegNo":"%s"}
						""".formatted("R".repeat(51))),
				Arguments.of("""
						{"name":"Valid Name","contactEmail":"not-an-email"}
						"""),
				Arguments.of("""
						{"name":"Valid Name","contactEmail":"%s@example.com"}
						""".formatted("e".repeat(250)))
		);
	}

	@Test
	void duplicateName_sameFirm_conflict_otherFirm_allowed() throws Exception {
		Session firmA = registerFirmAdmin("Dup Firm A");
		Session firmB = registerFirmAdmin("Dup Firm B");

		createClient(firmA.token(), "Acme Trading", null, null);
		createClient(firmB.token(), "Acme Trading", null, null);

		mockMvc.perform(post("/api/v1/clients")
						.header(HttpHeaders.AUTHORIZATION, bearer(firmA.token()))
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"name":"Acme Trading"}
								"""))
				.andExpect(status().isConflict());

		assertThat(clientRepository.findByFirmIdAndDeletedAtIsNull(firmA.firmId()))
				.extracting(Client::getName)
				.containsExactly("Acme Trading");
		assertThat(clientRepository.findByFirmIdAndDeletedAtIsNull(firmB.firmId()))
				.extracting(Client::getName)
				.containsExactly("Acme Trading");
	}

	@Test
	void createUser_rejectsCrossTenantClientId_allowsSameFirmClient() throws Exception {
		Session firmA = registerFirmAdmin("Access Firm A");
		Session firmB = registerFirmAdmin("Access Firm B");

		ClientResponse clientA = createClient(firmA.token(), "Client A", null, null);
		ClientResponse clientB = createClient(firmB.token(), "Client B", null, null);

		String suffix = uniqueSuffix();
		MvcResult rejected = mockMvc.perform(post("/api/v1/users")
						.header(HttpHeaders.AUTHORIZATION, bearer(firmA.token()))
						.contentType(MediaType.APPLICATION_JSON)
						.content(objectMapper.writeValueAsString(new CreateUserRequest(
								"blocked-" + suffix + "@example.com",
								PASSWORD,
								"Blocked User",
								"ACCOUNTANT",
								List.of(clientB.id())
						))))
				.andExpect(status().isNotFound())
				.andReturn();

		JsonNode error = objectMapper.readTree(rejected.getResponse().getContentAsString());
		assertThat(error.path("title").asText()).isEqualTo("Resource Not Found");
		assertThat(error.path("detail").asText()).doesNotContainIgnoringCase("another firm");
		assertThat(error.path("detail").asText()).doesNotContainIgnoringCase("access denied");

		assertThat(userClientAccessRepository.findAll())
				.noneMatch(access -> clientB.id().equals(access.getClientId()));

		MvcResult allowed = mockMvc.perform(post("/api/v1/users")
						.header(HttpHeaders.AUTHORIZATION, bearer(firmA.token()))
						.contentType(MediaType.APPLICATION_JSON)
						.content(objectMapper.writeValueAsString(new CreateUserRequest(
								"allowed-" + suffix + "@example.com",
								PASSWORD,
								"Allowed User",
								"ACCOUNTANT",
								List.of(clientA.id())
						))))
				.andExpect(status().isCreated())
				.andReturn();

		UserResponse createdUser = read(allowed, UserResponse.class);
		assertThat(userClientAccessRepository.findByUser_Id(createdUser.id()))
				.extracting(access -> access.getClientId())
				.containsExactly(clientA.id());
	}

	@Test
	void extraFirmIdInBody_isIgnored_clientBelongsToAuthenticatedFirm() throws Exception {
		Session firmA = registerFirmAdmin("Json Firm A");
		Session firmB = registerFirmAdmin("Json Firm B");

		MvcResult result = mockMvc.perform(post("/api/v1/clients")
						.header(HttpHeaders.AUTHORIZATION, bearer(firmA.token()))
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{
								  "name": "Malicious Client",
								  "firmId": "%s"
								}
								""".formatted(firmB.firmId())))
				.andExpect(status().isCreated())
				.andReturn();

		ClientResponse body = read(result, ClientResponse.class);
		assertThat(body.firmId()).isEqualTo(firmA.firmId());
		assertThat(body.firmId()).isNotEqualTo(firmB.firmId());

		Client stored = clientRepository.findById(body.id()).orElseThrow();
		assertThat(stored.getFirmId()).isEqualTo(firmA.firmId());
		assertThat(clientRepository.findByIdAndFirmId(body.id(), firmB.firmId())).isEmpty();
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

	private ClientResponse createClient(String token, String name, String businessRegNo, String contactEmail)
			throws Exception {
		var payload = objectMapper.createObjectNode().put("name", name);
		if (businessRegNo != null) {
			payload.put("businessRegNo", businessRegNo);
		}
		if (contactEmail != null) {
			payload.put("contactEmail", contactEmail);
		}
		MvcResult result = mockMvc.perform(post("/api/v1/clients")
						.header(HttpHeaders.AUTHORIZATION, bearer(token))
						.contentType(MediaType.APPLICATION_JSON)
						.content(objectMapper.writeValueAsString(payload)))
				.andExpect(status().isCreated())
				.andReturn();
		return read(result, ClientResponse.class);
	}

	private long clientCount(UUID firmId) {
		return clientRepository.findByFirmIdAndDeletedAtIsNull(firmId).size();
	}

	private Optional<AuditLog> clientCreatedAudit(UUID clientId, UUID firmId) {
		return auditLogRepository.findAll().stream()
				.filter(log -> AuditAction.CLIENT_CREATED.name().equals(log.getAction()))
				.filter(log -> AuditResourceType.CLIENT.name().equals(log.getResourceType()))
				.filter(log -> clientId.equals(log.getResourceId()))
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
