package com.finance.platform.support;



import org.testcontainers.containers.PostgreSQLContainer;

import org.testcontainers.utility.DockerImageName;



/**

 * Single Postgres instance shared across integration test classes so the JVM does not

 * stop the container between {@link AbstractPostgresIntegrationTest} subclasses.

 * <p>

 * Fails fast when Docker is unavailable so CI and local runs cannot silently skip PostgreSQL suites.

 */

public final class PostgresTestContainer {



	private static final DockerImageName IMAGE = DockerImageName.parse("postgres:16-alpine");



	private static final PostgreSQLContainer<?> CONTAINER = new PostgreSQLContainer<>(IMAGE)

			.withDatabaseName("finance_platform_test")

			.withUsername("test")

			.withPassword("test");



	private PostgresTestContainer() {

	}



	static {

		if (!org.testcontainers.DockerClientFactory.instance().isDockerAvailable()) {

			throw new IllegalStateException(

					"Docker is required for PostgreSQL integration tests. "

							+ "Start Docker Desktop (or the Docker daemon) and re-run.");

		}

		try {

			CONTAINER.start();

		} catch (Exception ex) {

			throw new IllegalStateException("Failed to start PostgreSQL Testcontainer: " + ex.getMessage(), ex);

		}

	}



	public static PostgreSQLContainer<?> get() {

		return CONTAINER;

	}

}


