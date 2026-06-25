package com.portfolio.farewatch.worker;

import com.portfolio.farewatch.queue.SweepQueue;
import com.portfolio.farewatch.queue.SweepQueue.Job;
import com.portfolio.farewatch.service.PollService;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Consumes poll jobs from the sharded queue. Every app instance has one of these
 * with a unique consumer name, so adding instances adds poll throughput (the
 * consumer group splits the stream across them). A job is polled then acked;
 * if the poll throws, it is left unacked so it can be reclaimed later.
 */
@Component
public class PollWorker {

	private static final Logger log = LoggerFactory.getLogger(PollWorker.class);

	private final SweepQueue queue;
	private final PollService pollService;
	private final String consumer = "worker-" + UUID.randomUUID();

	public PollWorker(SweepQueue queue, PollService pollService) {
		this.queue = queue;
		this.pollService = pollService;
	}

	/** Poll up to {@code max} jobs claimed from the shared queue; returns how many succeeded. */
	public int drain(int max) {
		List<Job> jobs = queue.read(consumer, max);
		int done = 0;
		for (Job j : jobs) {
			try {
				pollService.poll(j.watchId());
				queue.ack(j.recordId());
				done++;
			} catch (RuntimeException e) {
				log.warn("poll failed for watch {} (left for reclaim): {}", j.watchId(), e.toString());
			}
		}
		return done;
	}

	/**
	 * Crash recovery: take over jobs a dead worker left pending (idle &gt; {@code minIdle}),
	 * dead-lettering any that have failed {@code maxDeliveries} times, and poll the rest.
	 * Returns how many reclaimed jobs were polled successfully.
	 */
	public int recover(Duration minIdle, int maxDeliveries, int max) {
		List<Job> jobs = queue.reclaim(consumer, minIdle, maxDeliveries, max);
		int done = 0;
		for (Job j : jobs) {
			try {
				pollService.poll(j.watchId());
				queue.ack(j.recordId());
				done++;
			} catch (RuntimeException e) {
				log.warn("reclaimed poll failed for watch {} (left for next reclaim): {}", j.watchId(), e.toString());
			}
		}
		return done;
	}

	public String consumerName() {
		return consumer;
	}
}
