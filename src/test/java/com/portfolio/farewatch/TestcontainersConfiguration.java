package com.portfolio.farewatch;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

@TestConfiguration(proxyBeanMethods = false)
class TestcontainersConfiguration {

	@Bean
	@ServiceConnection
	PostgreSQLContainer postgresContainer() {
		// NB: Boot 4.1's org.testcontainers.postgresql.PostgreSQLContainer is non-generic.
		return new PostgreSQLContainer(DockerImageName.parse("postgres:16-alpine"));
	}

}
