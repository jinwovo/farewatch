package com.portfolio.farewatch;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
class FarewatchApplicationTests {

	@Test
	void contextLoads() {
		// Boots the full context against a real PostgreSQL (Testcontainers) and
		// applies the Flyway V1 migration — proves the P0 scaffold wires up.
	}

}
