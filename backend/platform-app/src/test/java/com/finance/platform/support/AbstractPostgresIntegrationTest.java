package com.finance.platform.support;

import com.finance.platform.auth.infrastructure.persistence.RoleJpaRepository;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.junit.jupiter.api.condition.EnabledIf;

@SpringBootTest
@ActiveProfiles("integrationtest")
@EnabledIf("com.finance.platform.support.PostgresTestContainer#isDockerAvailable")
public abstract class AbstractPostgresIntegrationTest extends BaseWebIntegrationTest {

	@Autowired
	private RoleJpaRepository roleRepository;

	protected IntegrationTestSupport support;

	@DynamicPropertySource
	static void registerProperties(DynamicPropertyRegistry registry) {
		var postgres = PostgresTestContainer.get();
		registry.add("spring.datasource.url", postgres::getJdbcUrl);
		registry.add("spring.datasource.username", postgres::getUsername);
		registry.add("spring.datasource.password", postgres::getPassword);
	}

	@BeforeEach
	void initIntegrationSupport() {
		support = new IntegrationTestSupport(mockMvc, roleRepository);
	}
}
