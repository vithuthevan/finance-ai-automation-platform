package com.finance.platform.security;

import com.finance.platform.platformadmin.PlatformAdminGrantJpaRepository;
import com.finance.platform.platformadmin.PlatformAdminService;
import com.finance.platform.support.AbstractPostgresIntegrationTest;
import com.finance.platform.support.IntegrationTestSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class PlatformAdminSecurityIntegrationTest extends AbstractPostgresIntegrationTest {

	@Autowired
	private PlatformAdminService platformAdminService;

	@Autowired
	private PlatformAdminGrantJpaRepository grantRepository;

	@BeforeEach
	void setUp() {
		support.seedRolesIfNeeded();
	}

	@Test
	void persistedGrantAllowsPlatformAccess_andRevokeBlocks() throws Exception {
		var admin = support.registerFirmAdmin("Platform Ops");
		platformAdminService.grant(admin.userId(), null, "test grant");

		mockMvc.perform(get("/api/v1/platform/metrics")
						.header(HttpHeaders.AUTHORIZATION, IntegrationTestSupport.bearer(admin.token())))
				.andExpect(status().isOk());

		platformAdminService.revoke(admin.userId(), admin.userId(), "test revoke");
		assertThat(grantRepository.findByUserId(admin.userId())).get().extracting("active").isEqualTo(false);

		String freshToken = support.login(admin.email());
		mockMvc.perform(get("/api/v1/platform/metrics")
						.header(HttpHeaders.AUTHORIZATION, IntegrationTestSupport.bearer(freshToken)))
				.andExpect(status().isForbidden());
	}

	@Test
	void platformMetricsDoNotExposeClientFinancialRecords() throws Exception {
		var admin = support.registerFirmAdmin("Platform Privacy");
		platformAdminService.grant(admin.userId(), null, "test");

		var result = mockMvc.perform(get("/api/v1/platform/firms")
						.header(HttpHeaders.AUTHORIZATION, IntegrationTestSupport.bearer(admin.token())))
				.andExpect(status().isOk())
				.andReturn();

		String body = result.getResponse().getContentAsString();
		assertThat(body).doesNotContain("expense").doesNotContain("income").doesNotContain("bankStatement");
	}
}
