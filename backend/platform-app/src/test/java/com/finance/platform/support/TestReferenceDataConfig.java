package com.finance.platform.support;

import com.finance.platform.auth.domain.model.Role;
import com.finance.platform.auth.infrastructure.persistence.RoleJpaRepository;
import com.finance.platform.finance.domain.model.SubscriptionPlan;
import com.finance.platform.finance.infrastructure.persistence.SubscriptionPlanJpaRepository;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Profile;

@TestConfiguration
@Profile("test")
public class TestReferenceDataConfig {

	@Bean
	ApplicationRunner seedReferenceData(
			RoleJpaRepository roleRepository,
			SubscriptionPlanJpaRepository planRepository
	) {
		return args -> {
			if (roleRepository.count() == 0) {
				roleRepository.save(Role.builder().id((short) 1).code(Role.RoleCode.ADMIN).name("Firm Administrator").build());
				roleRepository.save(Role.builder().id((short) 2).code(Role.RoleCode.ACCOUNTANT).name("Accountant").build());
				roleRepository.save(Role.builder().id((short) 3).code(Role.RoleCode.AUDITOR).name("Auditor").build());
				roleRepository.save(Role.builder().id((short) 4).code(Role.RoleCode.BUSINESS_OWNER).name("Business Owner").build());
			}
			if (planRepository.count() == 0) {
				planRepository.save(SubscriptionPlan.builder()
						.code("STARTER")
						.name("Starter")
						.description("Test starter plan")
						.active(true)
						.maxClients(50)
						.maxUsers(10)
						.monthlyDocumentLimit(500)
						.monthlyAiLimit(100)
						.storageLimitBytes(2_147_483_648L)
						.features("AI_EXTRACTION,BANK_RECONCILIATION")
						.build());
				planRepository.save(SubscriptionPlan.builder()
						.code("PRACTICE")
						.name("Practice")
						.description("Test practice plan")
						.active(true)
						.maxClients(50)
						.maxUsers(10)
						.monthlyDocumentLimit(3000)
						.monthlyAiLimit(1000)
						.storageLimitBytes(10_737_418_240L)
						.features("AI_EXTRACTION,BANK_RECONCILIATION")
						.build());
			}
		};
	}
}
