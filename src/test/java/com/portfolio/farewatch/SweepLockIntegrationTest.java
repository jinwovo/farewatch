package com.portfolio.farewatch;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.portfolio.farewatch.domain.TripType;
import com.portfolio.farewatch.domain.Watch;
import com.portfolio.farewatch.lock.RedisDistributedLock;
import com.portfolio.farewatch.ratelimit.RedisRateLimiter;
import com.portfolio.farewatch.repo.PriceAlertRepository;
import com.portfolio.farewatch.repo.PricePointRepository;
import com.portfolio.farewatch.repo.WatchRepository;
import com.portfolio.farewatch.service.FareSweepService;
import com.portfolio.farewatch.service.FareSweepService.SweepResult;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
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
 * The headline P2 proof: the hourly sweep never double-polls across instances.
 * Runs against a real PostgreSQL (Testcontainers, via {@link TestcontainersConfiguration})
 * AND a real Redis (Testcontainers) backing the distributed lock.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@Testcontainers
class SweepLockIntegrationTest {

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
	RedisDistributedLock lock;
	@Autowired
	RedisRateLimiter rateLimiter;
	@Autowired
	WatchRepository watches;
	@Autowired
	PricePointRepository pricePoints;
	@Autowired
	PriceAlertRepository alerts;

	@Test
	void redis_lock_is_mutually_exclusive() {
		String key = "farewatch:test:lock:" + UUID.randomUUID();
		assertTrue(lock.tryLock(key, "owner-A", Duration.ofSeconds(30)));
		assertFalse(lock.tryLock(key, "owner-B", Duration.ofSeconds(30)));  // already held
		assertFalse(lock.unlock(key, "owner-B"));                           // a foreign token can't release it
		assertTrue(lock.unlock(key, "owner-A"));
		assertTrue(lock.tryLock(key, "owner-B", Duration.ofSeconds(30)));   // free again
		lock.unlock(key, "owner-B");
	}

	@Test
	void token_bucket_rate_limiter_allows_burst_then_denies() {
		String key = "test:" + UUID.randomUUID();
		// negligible refill rate, capacity 3 → 3 allowed, 4th denied
		assertTrue(rateLimiter.tryAcquire(key, 0.001, 3));
		assertTrue(rateLimiter.tryAcquire(key, 0.001, 3));
		assertTrue(rateLimiter.tryAcquire(key, 0.001, 3));
		assertFalse(rateLimiter.tryAcquire(key, 0.001, 3));
	}

	@Test
	void sweep_does_not_double_poll_while_another_instance_holds_the_lock() {
		Watch w = watches.save(dueWatch());

		// Simulate a second instance currently holding the sweep lock.
		assertTrue(lock.tryLock(FareSweepService.LOCK_KEY, "instance-B", Duration.ofSeconds(60)));

		SweepResult skipped = sweep.run(); // this instance tries while B holds the lock
		assertFalse(skipped.ran());
		assertTrue(pricePoints.findByWatch_IdOrderByObservedAtAsc(w.getId()).isEmpty(), "must not double-poll");

		assertTrue(lock.unlock(FareSweepService.LOCK_KEY, "instance-B"));

		SweepResult ran = sweep.run(); // now this instance wins the lock
		assertTrue(ran.ran());
		assertEquals(1, ran.polled());
		assertEquals(1, pricePoints.findByWatch_IdOrderByObservedAtAsc(w.getId()).size(), "polled exactly once");
		assertEquals(1, alerts.findByWatch_IdOrderByCreatedAtDesc(w.getId()).size(), "first low fires a NEW_LOW alert");
	}

	private Watch dueWatch() {
		Watch w = new Watch();
		w.setUserRef("sweep-test");
		w.setOrigin("ICN");
		w.setDestination("CDG");
		w.setTripType(TripType.ONE_WAY);
		w.setDepartDateFrom(LocalDate.now().plusDays(30));
		w.setDepartDateTo(LocalDate.now().plusDays(33));
		w.setNextPollAt(Instant.now().minusSeconds(120)); // overdue → eligible for the sweep
		return w;
	}
}
