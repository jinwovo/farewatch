package com.portfolio.farewatch;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.portfolio.farewatch.domain.TripType;
import com.portfolio.farewatch.domain.Watch;
import com.portfolio.farewatch.queue.SweepQueue;
import com.portfolio.farewatch.queue.SweepQueue.Job;
import com.portfolio.farewatch.repo.PricePointRepository;
import com.portfolio.farewatch.repo.WatchRepository;
import com.portfolio.farewatch.worker.PollWorker;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
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
 * Crash recovery: a worker can die mid-job (claimed but never acked) and the work is
 * not lost. Another worker reclaims the stale pending entry (XPENDING + XCLAIM) and
 * finishes it exactly once; a job that keeps failing is dead-lettered instead of
 * looping forever.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@Testcontainers
class ChaosRecoveryIntegrationTest {

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
	@Autowired
	PollWorker worker;
	@Autowired
	WatchRepository watches;
	@Autowired
	PricePointRepository pricePoints;

	@Test
	void dead_worker_job_is_reclaimed_and_polled_exactly_once() throws InterruptedException {
		Watch a = watches.save(dueWatch("ICN", "SIN"));
		queue.enqueue(a.getId());

		// a worker claims the job, then dies before acking (never polls)
		List<Job> abandoned = queue.read("worker-DEAD", 10);
		assertEquals(1, abandoned.size());
		assertEquals(0, pricePoints.findByWatch_IdOrderByObservedAtAsc(a.getId()).size());

		// after the job sits idle past the threshold, a live worker takes it over and finishes it
		Thread.sleep(120);
		int recovered = worker.recover(Duration.ofMillis(50), 5, 10);
		assertEquals(1, recovered);
		assertEquals(1, pricePoints.findByWatch_IdOrderByObservedAtAsc(a.getId()).size()); // polled exactly once

		// nothing left pending — a second reclaim finds nothing to do
		assertEquals(0, queue.reclaim("worker-LIVE2", Duration.ofMillis(50), 5, 10).size());
	}

	@Test
	void job_that_keeps_failing_is_dead_lettered() throws InterruptedException {
		long dlqBefore = queue.deadLetterSize();
		Watch b = watches.save(dueWatch("ICN", "BKK"));
		queue.enqueue(b.getId());

		queue.read("worker-D1", 10); // delivery #1, owner dies
		Thread.sleep(80);
		// delivery count 1 < maxDeliveries(2): reclaimed for a retry, owner dies again
		List<Job> retry = queue.reclaim("worker-D2", Duration.ofMillis(50), 2, 10);
		assertEquals(1, retry.size());

		Thread.sleep(80);
		// delivery count now 2 >= maxDeliveries(2): given up on → dead-lettered, not returned
		List<Job> giveUp = queue.reclaim("worker-D3", Duration.ofMillis(50), 2, 10);
		assertEquals(0, giveUp.size());
		assertEquals(dlqBefore + 1, queue.deadLetterSize());

		// and it no longer lingers in the main group's pending list
		assertEquals(0, queue.reclaim("worker-D4", Duration.ofMillis(50), 2, 10).size());
	}

	private Watch dueWatch(String origin, String dest) {
		Watch w = new Watch();
		w.setUserRef("chaos-test");
		w.setOrigin(origin);
		w.setDestination(dest);
		w.setTripType(TripType.ONE_WAY);
		w.setDepartDateFrom(LocalDate.now().plusDays(30));
		w.setDepartDateTo(LocalDate.now().plusDays(30));
		w.setNextPollAt(Instant.now().minusSeconds(60));
		return w;
	}
}
