package com.finance.platform.support;

import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * Single Postgres instance shared across integration test classes so the JVM does not
 * stop the container between {@link AbstractPostgresIntegrationTest} subclasses.
 */
public final class PostgresTestContainer {

	private static final DockerImageName IMAGE = DockerImageName.parse("postgres:16-alpine");

	private static final PostgreSQLContainer<?> CONTAINER = new PostgreSQLContainer<>(IMAGE)
			.withDatabaseName("finance_platform_test")
			.withUsername("test")
			.withPassword("test");

	private static final boolean STARTED;

	static {
		boolean started = false;
		try {
			if (org.testcontainers.DockerClientFactory.instance().isDockerAvailable()) {
				CONTAINER.start();
				started = true;
			}
		} catch (Exception ignored) {
			started = false;
		}
		STARTED = started;
	}

	private PostgresTestContainer() {
	}

	public static PostgreSQLContainer<?> get() {
		return CONTAINER;
	}

	public static boolean isDockerAvailable() {
		return STARTED;
	}
}
