package com.finance.platform.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.finance.platform.auth.application.dto.ChangePasswordRequest;
import com.finance.platform.auth.application.dto.RegisterRequest;
import com.finance.platform.auth.application.dto.UpdateUserRequest;
import com.finance.platform.support.AbstractPostgresIntegrationTest;
import com.finance.platform.support.IntegrationTestSupport;
import com.finance.platform.support.TestTags;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MvcResult;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Tag(TestTags.POSTGRES_INTEGRATION)
@Tag(TestTags.SESSION_REVOCATION)
@TestPropertySource(properties = {
		"app.auth.rate-limit-enabled=false"
})
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class SessionRevocationIntegrationTest extends AbstractPostgresIntegrationTest {

	@Autowired
	private ObjectMapper objectMapper;

	@BeforeEach
	void seed() throws Exception {
		support.seedRolesIfNeeded();
	}

	@Test
	void loginAndRefresh_rotatesRefreshToken() throws Exception {
		IntegrationTestSupport.AuthTokens tokens = registerAndLogin();
		MvcResult refreshed = support.refresh(tokens.refreshToken());
		assertThat(refreshed.getResponse().getStatus()).isEqualTo(200);
		String rotated = support.readRefreshToken(refreshed);
		assertThat(rotated).isNotBlank().isNotEqualTo(tokens.refreshToken());

		MvcResult stale = support.refresh(tokens.refreshToken());
		assertThat(stale.getResponse().getStatus()).isEqualTo(422);
	}

	@Test
	void logout_revokesRefreshToken_idempotent() throws Exception {
		IntegrationTestSupport.AuthTokens tokens = registerAndLogin();
		assertThat(support.logout(tokens.refreshToken()).getResponse().getStatus()).isEqualTo(200);
		assertThat(support.refresh(tokens.refreshToken()).getResponse().getStatus()).isEqualTo(422);
		assertThat(support.logout(tokens.refreshToken()).getResponse().getStatus()).isEqualTo(200);
	}

	@Test
	void passwordChange_invalidatesAccessAndRefresh() throws Exception {
		IntegrationTestSupport.AuthTokens tokens = registerAndLogin();
		String access = tokens.accessToken();

		mockMvc.perform(post("/api/v1/users/me/password")
						.header(HttpHeaders.AUTHORIZATION, IntegrationTestSupport.bearer(access))
						.contentType(MediaType.APPLICATION_JSON)
						.content(objectMapper.writeValueAsString(
								new ChangePasswordRequest(IntegrationTestSupport.PASSWORD, "new-password-9"))))
				.andExpect(status().isNoContent());

		mockMvc.perform(get("/api/v1/clients")
						.header(HttpHeaders.AUTHORIZATION, IntegrationTestSupport.bearer(access)))
				.andExpect(status().isUnauthorized());

		assertThat(support.refresh(tokens.refreshToken()).getResponse().getStatus()).isEqualTo(422);
	}

	@Test
	void disabledUser_rejectsAccessAndRefresh() throws Exception {
		var admin = support.registerFirmAdmin("Disable Firm");
		StaffSession staff = createStaff(admin, "ACCOUNTANT");

		mockMvc.perform(post("/api/v1/users/" + staff.userId() + "/deactivate")
						.header(HttpHeaders.AUTHORIZATION, IntegrationTestSupport.bearer(admin.token())))
				.andExpect(status().isOk());

		mockMvc.perform(get("/api/v1/clients")
						.header(HttpHeaders.AUTHORIZATION, IntegrationTestSupport.bearer(staff.tokens().accessToken())))
				.andExpect(status().isUnauthorized());

		assertThat(support.refresh(staff.tokens().refreshToken()).getResponse().getStatus()).isEqualTo(422);
	}

	@Test
	void roleDowngrade_takesEffectImmediately() throws Exception {
		var admin = support.registerFirmAdmin("Role Firm");
		StaffSession accountant = createStaff(admin, "ACCOUNTANT");
		String access = accountant.tokens().accessToken();

		mockMvc.perform(put("/api/v1/users/" + accountant.userId())
						.header(HttpHeaders.AUTHORIZATION, IntegrationTestSupport.bearer(admin.token()))
						.contentType(MediaType.APPLICATION_JSON)
						.content(objectMapper.writeValueAsString(new UpdateUserRequest(null, null, "AUDITOR"))))
				.andExpect(status().isOk());

		mockMvc.perform(post("/api/v1/clients")
						.header(HttpHeaders.AUTHORIZATION, IntegrationTestSupport.bearer(access))
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"name\":\"Should Fail\"}"))
				.andExpect(status().isForbidden());
	}

	@Test
	@Order(Integer.MAX_VALUE)
	void concurrentRefresh_onlyOneSucceeds() throws Exception {
		IntegrationTestSupport.AuthTokens tokens = registerAndLogin();
		ExecutorService pool = Executors.newFixedThreadPool(2);
		try {
			CountDownLatch ready = new CountDownLatch(2);
			CountDownLatch start = new CountDownLatch(1);
			AtomicInteger successes = new AtomicInteger();
			List<Callable<Void>> tasks = List.of(
					() -> {
						ready.countDown();
						start.await();
						int status = createMockMvc().perform(post("/api/v1/auth/refresh")
										.contentType(MediaType.APPLICATION_JSON)
										.content("{\"refreshToken\":\"" + tokens.refreshToken() + "\"}"))
								.andReturn().getResponse().getStatus();
						if (status == 200) {
							successes.incrementAndGet();
						}
						return null;
					},
					() -> {
						ready.countDown();
						start.await();
						int status = createMockMvc().perform(post("/api/v1/auth/refresh")
										.contentType(MediaType.APPLICATION_JSON)
										.content("{\"refreshToken\":\"" + tokens.refreshToken() + "\"}"))
								.andReturn().getResponse().getStatus();
						if (status == 200) {
							successes.incrementAndGet();
						}
						return null;
					}
			);
			List<Future<Void>> futures = new ArrayList<>();
			for (Callable<Void> task : tasks) {
				futures.add(pool.submit(task));
			}
			ready.await();
			start.countDown();
			for (Future<Void> future : futures) {
				future.get();
			}
			assertThat(successes.get()).isEqualTo(1);
		} finally {
			pool.shutdownNow();
		}
	}

	@Test
	void refreshReuse_revokesAllSessions() throws Exception {
		IntegrationTestSupport.AuthTokens tokens = registerAndLogin();
		MvcResult first = support.refresh(tokens.refreshToken());
		assertThat(first.getResponse().getStatus()).isEqualTo(200);
		String rotated = support.readRefreshToken(first);
		assertThat(rotated).isNotBlank();

		assertThat(support.refresh(tokens.refreshToken()).getResponse().getStatus()).isEqualTo(422);
		assertThat(support.refresh(rotated).getResponse().getStatus()).isEqualTo(422);
	}

	@Test
	void crossUserRefresh_doesNotAffectOtherUser() throws Exception {
		IntegrationTestSupport.AuthTokens userA = registerAndLogin();
		IntegrationTestSupport.AuthTokens userB = registerAndLogin();

		MvcResult rotated = support.refresh(userA.refreshToken());
		assertThat(rotated.getResponse().getStatus()).isEqualTo(200);
		support.refresh(userA.refreshToken());

		assertThat(support.refresh(userB.refreshToken()).getResponse().getStatus()).isEqualTo(200);
	}

	private IntegrationTestSupport.AuthTokens registerAndLogin() throws Exception {
		String suffix = IntegrationTestSupport.uniqueSuffix();
		mockMvc.perform(post("/api/v1/auth/register")
						.contentType(MediaType.APPLICATION_JSON)
						.content(objectMapper.writeValueAsString(new RegisterRequest(
								"Session Firm " + suffix,
								null,
								"sess-" + suffix + "@example.com",
								IntegrationTestSupport.PASSWORD,
								"Admin " + suffix
						))))
				.andExpect(status().isCreated());
		return support.loginSession("sess-" + suffix + "@example.com");
	}

	private StaffSession createStaff(IntegrationTestSupport.Session admin, String role) throws Exception {
		String suffix = IntegrationTestSupport.uniqueSuffix();
		String email = role.toLowerCase() + "-" + suffix + "@example.com";
		MvcResult created = mockMvc.perform(post("/api/v1/users")
						.header(HttpHeaders.AUTHORIZATION, IntegrationTestSupport.bearer(admin.token()))
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{
								  "email":"%s",
								  "password":"%s",
								  "fullName":"Staff %s",
								  "role":"%s"
								}
								""".formatted(email, IntegrationTestSupport.PASSWORD, suffix, role)))
				.andExpect(status().isCreated())
				.andReturn();
		java.util.UUID userId = java.util.UUID.fromString(
				objectMapper.readTree(created.getResponse().getContentAsString()).get("id").asText());
		return new StaffSession(userId, support.loginSession(email));
	}

	private record StaffSession(java.util.UUID userId, IntegrationTestSupport.AuthTokens tokens) {
	}
}
