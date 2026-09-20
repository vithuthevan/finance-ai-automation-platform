package com.finance.platform.controller;

import com.finance.platform.support.AbstractPostgresIntegrationTest;
import com.finance.platform.support.IntegrationTestSupport.Session;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class MonthEndCommandCenterIntegrationTest extends AbstractPostgresIntegrationTest {

	private Session admin;

	@BeforeEach
	void setUp() throws Exception {
		support.seedRolesIfNeeded();
		admin = support.registerFirmAdmin("Command Center Firm");
	}

	@Test
	void commandCenterReturnsPortfolioShape() throws Exception {
		mockMvc.perform(get("/api/v1/work/month-end-command-center")
						.header("Authorization", "Bearer " + admin.token())
						.accept(MediaType.APPLICATION_JSON))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.periodLabel").exists())
				.andExpect(jsonPath("$.summary.totalClients").isNumber())
				.andExpect(jsonPath("$.clients").isArray());
	}

	@Test
	void onboardingChecklistSeededCategoriesAfterRegistration() throws Exception {
		mockMvc.perform(get("/api/v1/work/onboarding-checklist")
						.header("Authorization", "Bearer " + admin.token()))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.steps[1].code").value("CONFIRM_CATEGORIES"))
				.andExpect(jsonPath("$.steps[1].completed").value(true));
	}

	@Test
	void accountantCannotAccessOnboardingChecklist() throws Exception {
		String email = "acct-" + System.nanoTime() + "@firm.test";
		mockMvc.perform(post("/api/v1/users")
						.header("Authorization", "Bearer " + admin.token())
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"fullName":"Acct","email":"%s","password":"password1","role":"ACCOUNTANT"}
								""".formatted(email)))
				.andExpect(status().isCreated());
		String token = support.login(email);
		mockMvc.perform(get("/api/v1/work/onboarding-checklist")
						.header("Authorization", "Bearer " + token))
				.andExpect(status().isForbidden());
	}
}
