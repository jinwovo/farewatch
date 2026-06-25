package com.portfolio.farewatch;

import static org.junit.jupiter.api.Assertions.assertTrue;

import com.portfolio.farewatch.queue.SweepQueue;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

/**
 * The sweep stream can't grow without bound: every enqueue approximate-trims it to
 * {@code stream-maxlen}, so under a flood the length stays bounded instead of leaking
 * Redis memory forever. (Trimming is safe because a still-due watch is re-enqueued next tick.)
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest(properties = "farewatch.sweep.stream-maxlen=50")
@Testcontainers
class StreamTrimIntegrationTest {

	@Container
	static GenericContainer<?> redis = new GenericContainer<>(DockerImageName.parse("redis:7-alpine"))
			.withExposedPorts(6379);

	@DynamicPropertySource
	static void redisProperties(DynamicPropertyRegistry registry) {
		registry.add("spring.data.redis.host", redis::getHost);
		registry.add("spring.data.redis.port", () -> redis.getMappedPort(6379));
	}

	@Autowired
	SweepQueue queue;

	@Test
	void stream_length_stays_bounded_under_a_flood() {
		int flood = 1000;
		for (int i = 0; i < flood; i++) {
			queue.enqueue(UUID.randomUUID());
		}
		long size = queue.streamSize();
		// Approximate XTRIM (~) keeps somewhat more than maxlen (whole macro-nodes), but the point
		// is it does NOT grow with the flood: 1000 enqueued, length stays a small bounded fraction.
		assertTrue(size > 0 && size < flood / 2,
				"stream should be trimmed well below the flood size, was " + size);
	}
}
