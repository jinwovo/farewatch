package com.portfolio.farewatch;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.portfolio.farewatch.domain.TripType;
import com.portfolio.farewatch.domain.Watch;
import com.portfolio.farewatch.queue.SweepQueue;
import com.portfolio.farewatch.queue.SweepQueue.Job;
import com.portfolio.farewatch.repo.PricePointRepository;
import com.portfolio.farewatch.repo.WatchRepository;
import com.portfolio.farewatch.service.PollService;
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
 * Horizontal scale: two workers reading from the same Redis Streams consumer group
 * split the enqueued jobs — each job is delivered to exactly one worker, so each
 * watch is polled exactly once (no double-processing across instances).
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@Testcontainers
class QueueShardingIntegrationTest {

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
	PollService pollService;
	@Autowired
	WatchRepository watches;
	@Autowired
	PricePointRepository pricePoints;

	@Test
	void consumer_group_delivers_each_job_to_exactly_one_worker() {
		Watch a = watches.save(dueWatch("ICN", "NRT"));
		Watch b = watches.save(dueWatch("ICN", "KIX"));
		Watch c = watches.save(dueWatch("ICN", "FUK"));
		queue.enqueue(a.getId());
		queue.enqueue(b.getId());
		queue.enqueue(c.getId());

		// two distinct consumers in the same group claim disjoint sets of jobs
		List<Job> jobsA = queue.read("worker-A", 2);
		List<Job> jobsB = queue.read("worker-B", 5);
		assertEquals(3, jobsA.size() + jobsB.size());

		for (Job j : jobsA) {
			pollService.poll(j.watchId());
			queue.ack(j.recordId());
		}
		for (Job j : jobsB) {
			pollService.poll(j.watchId());
			queue.ack(j.recordId());
		}

		// each watch polled exactly once — no job went to both workers
		assertEquals(1, pricePoints.findByWatch_IdOrderByObservedAtAsc(a.getId()).size());
		assertEquals(1, pricePoints.findByWatch_IdOrderByObservedAtAsc(b.getId()).size());
		assertEquals(1, pricePoints.findByWatch_IdOrderByObservedAtAsc(c.getId()).size());
	}

	private Watch dueWatch(String origin, String dest) {
		Watch w = new Watch();
		w.setUserRef("queue-test");
		w.setOrigin(origin);
		w.setDestination(dest);
		w.setTripType(TripType.ONE_WAY);
		w.setDepartDateFrom(LocalDate.now().plusDays(30));
		w.setDepartDateTo(LocalDate.now().plusDays(30));
		w.setNextPollAt(Instant.now().minusSeconds(60));
		return w;
	}
}
