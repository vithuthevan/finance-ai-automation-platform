package com.finance.platform.support;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.finance.platform.auth.application.dto.LoginRequest;
import com.finance.platform.auth.application.dto.LoginResponse;
import com.finance.platform.auth.application.dto.RegisterRequest;
import com.finance.platform.auth.application.dto.RegisterResponse;
import com.finance.platform.auth.domain.model.Role;
import com.finance.platform.auth.infrastructure.persistence.RoleJpaRepository;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

public class IntegrationTestSupport {

	public static final String PASSWORD = "password1";

	private final MockMvc mockMvc;
	private final ObjectMapper objectMapper;
	private final RoleJpaRepository roleRepository;

	public IntegrationTestSupport(MockMvc mockMvc, RoleJpaRepository roleRepository) {
		this.mockMvc = mockMvc;
		this.objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());
		this.roleRepository = roleRepository;
	}

	public void seedRolesIfNeeded() {
		if (roleRepository.count() > 0) {
			return;
		}
		roleRepository.save(Role.builder().id((short) 1).code(Role.RoleCode.ADMIN).name("Firm Administrator").build());
		roleRepository.save(Role.builder().id((short) 2).code(Role.RoleCode.ACCOUNTANT).name("Accountant").build());
		roleRepository.save(Role.builder().id((short) 3).code(Role.RoleCode.AUDITOR).name("Auditor").build());
		roleRepository.save(Role.builder().id((short) 4).code(Role.RoleCode.BUSINESS_OWNER).name("Business Owner").build());
	}

	public Session registerFirmAdmin(String firmNamePrefix) throws Exception {
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

	public String login(String email) throws Exception {
		MvcResult result = mockMvc.perform(post("/api/v1/auth/login")
						.contentType(MediaType.APPLICATION_JSON)
						.content(objectMapper.writeValueAsString(new LoginRequest(email, PASSWORD))))
				.andExpect(status().isOk())
				.andReturn();
		return read(result, LoginResponse.class).accessToken();
	}

	public <T> T read(MvcResult result, Class<T> type) throws Exception {
		return objectMapper.readValue(result.getResponse().getContentAsString(), type);
	}

	public static String bearer(String token) {
		return "Bearer " + token;
	}

	public static String uniqueSuffix() {
		return UUID.randomUUID().toString().substring(0, 8);
	}

	public record Session(UUID firmId, UUID userId, String email, String token) {
	}
}
