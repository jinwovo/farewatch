package com.portfolio.farewatch;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.portfolio.farewatch.domain.TripType;
import com.portfolio.farewatch.domain.Watch;
import com.portfolio.farewatch.repo.PricePointRepository;
import com.portfolio.farewatch.repo.WatchRepository;
import com.portfolio.farewatch.service.FareSweepService;
import com.portfolio.farewatch.service.FareSweepService.SweepResult;
import java.time.Instant;
import java.time.LocalDate;
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
 * Adaptive polling: with the per-tick budget (1) smaller than the number of due
 * watches (2), the sweep polls the highest-value one first — here the watch
 * departing soonest — and defers the rest to the next tick.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest(properties = "farewatch.sweep.budget-per-tick=1")
@Testcontainers
class AdaptiveSweepIntegrationTest {

	@Container
	static GenericContainer<?> redis = new GenericContainer<>(DockerImageName.parse("redis:7-alpine"))
			.withExposedPorts(6379);

	@DynamicPropertySource
	static void redisProperties(DynamicPropertyRegistry registry) {
		registry.add("spring.data.redis.host", redis::getHost);
		registry.add("spring.data.redis.port", () -> redis.getMappedPort(6379));
	}

	@Autowired
	FareSweepService sweep;
	@Autowired
	WatchRepository watches;
	@Autowired
	PricePointRepository pricePoints;

	@Test
	void sweep_polls_the_highest_value_watch_first_under_budget() {
		Watch near = watches.save(dueWatch("ICN", "FUK", LocalDate.now().plusDays(3)));   // departs soon
		Watch far = watches.save(dueWatch("ICN", "JFK", LocalDate.now().plusDays(300)));  // departs far off

		SweepResult result = sweep.run();

		assertTrue(result.ran());
		assertEquals(1, result.polled());  // budget = 1
		assertEquals(1, result.skipped()); // the other due watch is deferred
		assertEquals(1, pricePoints.findByWatch_IdOrderByObservedAtAsc(near.getId()).size());
		assertEquals(0, pricePoints.findByWatch_IdOrderByObservedAtAsc(far.getId()).size());
	}

	private Watch dueWatch(String origin, String dest, LocalDate depart) {
		Watch w = new Watch();
		w.setUserRef("adaptive-test");
		w.setOrigin(origin);
		w.setDestination(dest);
		w.setTripType(TripType.ONE_WAY);
		w.setDepartDateFrom(depart);
		w.setDepartDateTo(depart);
		w.setNextPollAt(Instant.now().minusSeconds(120)); // overdue
		return w;
	}
}
