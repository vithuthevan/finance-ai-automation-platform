package com.finance.platform.security;

import com.finance.platform.finance.domain.model.FirmSubscription;
import com.finance.platform.finance.infrastructure.persistence.FirmSubscriptionJpaRepository;
import com.finance.platform.support.AbstractPostgresIntegrationTest;
import com.finance.platform.support.IntegrationTestSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;

import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class SubscriptionQuotaConcurrencyIntegrationTest extends AbstractPostgresIntegrationTest {

	@Autowired
	private FirmSubscriptionJpaRepository subscriptionRepository;

	@BeforeEach
	void setUp() {
		support.seedRolesIfNeeded();
	}

	@Test
	void concurrentClientCreates_doNotExceedPlanLimit() throws Exception {
		var admin = support.registerFirmAdmin("Quota Firm");
		FirmSubscription subscription = subscriptionRepository.findByFirmId(admin.firmId()).orElseThrow();
		subscription.setMaxClients(50);
		subscriptionRepository.save(subscription);

		for (int i = 0; i < 49; i++) {
			createClient(admin.token(), "Client " + i);
		}

		ExecutorService pool = Executors.newFixedThreadPool(2);
		try {
			List<Callable<Integer>> tasks = List.of(
					() -> attemptCreate(admin.token(), "Race A"),
					() -> attemptCreate(admin.token(), "Race B")
			);
			List<Future<Integer>> results = pool.invokeAll(tasks);
			int successes = 0;
			for (Future<Integer> result : results) {
				successes += result.get();
			}
			assertThat(successes).isEqualTo(1);
		} finally {
			pool.shutdownNow();
		}
	}

	private void createClient(String token, String name) throws Exception {
		mockMvc.perform(post("/api/v1/clients")
						.header(HttpHeaders.AUTHORIZATION, IntegrationTestSupport.bearer(token))
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"name\":\"" + name + "\"}"))
				.andExpect(status().isCreated());
	}

	private int attemptCreate(String token, String name) {
		try {
			var status = mockMvc.perform(post("/api/v1/clients")
							.header(HttpHeaders.AUTHORIZATION, IntegrationTestSupport.bearer(token))
							.contentType(MediaType.APPLICATION_JSON)
							.content("{\"name\":\"" + name + "\"}"))
					.andReturn()
					.getResponse()
					.getStatus();
			return status == 201 ? 1 : 0;
		} catch (Exception ex) {
			return 0;
		}
	}
}
