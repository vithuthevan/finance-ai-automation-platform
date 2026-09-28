package com.finance.platform.migration;

import com.finance.platform.support.TestTags;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.assertj.core.api.Assertions.assertThatCode;

@Testcontainers
@Tag(TestTags.FLYWAY)
@Tag(TestTags.POSTGRES_INTEGRATION)
class FlywayMigrationIntegrationTest {

	@Container
	static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine")
			.withDatabaseName("finance_platform_migration")
			.withUsername("test")
			.withPassword("test");

	@Test
	void allMigrationsApplyOnCleanDatabase() {
		assertThatCode(() -> {
			Flyway flyway = Flyway.configure()
					.dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
					.locations("classpath:db/migration")
					.load();
			flyway.migrate();
		}).doesNotThrowAnyException();
	}
}
