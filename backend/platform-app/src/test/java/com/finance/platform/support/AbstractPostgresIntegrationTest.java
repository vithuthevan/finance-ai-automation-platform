package com.finance.platform.support;

import com.finance.platform.auth.infrastructure.persistence.RoleJpaRepository;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest
@ActiveProfiles("integrationtest")
@Testcontainers(disabledWithoutDocker = true)
public abstract class AbstractPostgresIntegrationTest extends BaseWebIntegrationTest {

	@Container
	static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine")
			.withDatabaseName("finance_platform_test")
			.withUsername("test")
			.withPassword("test");

	@Autowired
	private RoleJpaRepository roleRepository;

	protected IntegrationTestSupport support;

	@DynamicPropertySource
	static void registerProperties(DynamicPropertyRegistry registry) {
		registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
		registry.add("spring.datasource.username", POSTGRES::getUsername);
		registry.add("spring.datasource.password", POSTGRES::getPassword);
	}

	@BeforeEach
	void initIntegrationSupport() {
		support = new IntegrationTestSupport(mockMvc, roleRepository);
	}
}
