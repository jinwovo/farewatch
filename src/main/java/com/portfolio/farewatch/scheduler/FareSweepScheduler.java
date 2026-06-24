package com.portfolio.farewatch.scheduler;

import com.portfolio.farewatch.notify.NotificationDispatcher;
import com.portfolio.farewatch.service.FareSweepService;
import com.portfolio.farewatch.service.FareSweepService.SweepResult;
import com.portfolio.farewatch.worker.PollWorker;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Drives the sweep on a fixed cadence (hourly by default). Every app instance runs
 * this: {@link FareSweepService#run()} enqueues due watches (deduped by the Redis
 * lock), then this instance's {@link PollWorker} drains its share of the shared
 * queue — so adding instances adds both enqueue resilience and poll throughput.
 */
@Component
public class FareSweepScheduler {

	private static final Logger log = LoggerFactory.getLogger(FareSweepScheduler.class);

	private final FareSweepService sweep;
	private final PollWorker worker;
	private final NotificationDispatcher dispatcher;
	private final int drainMax;

	public FareSweepScheduler(FareSweepService sweep, PollWorker worker, NotificationDispatcher dispatcher,
			@Value("${farewatch.sweep.budget-per-tick:100}") int budgetPerTick) {
		this.sweep = sweep;
		this.worker = worker;
		this.dispatcher = dispatcher;
		this.drainMax = Math.max(1, budgetPerTick) * 2;
	}

	@Scheduled(
			initialDelayString = "${farewatch.sweep.initial-delay-ms:3600000}",
			fixedDelayString = "${farewatch.sweep.interval-ms:3600000}")
	public void scheduled() {
		SweepResult result = sweep.run();
		int drained = worker.drain(drainMax);
		int notified = dispatcher.dispatch(drainMax * 2);
		if (result.ran() || drained > 0 || notified > 0) {
			log.info("sweep: enqueued {} (deferred {}), drained {}, notified {}",
					result.enqueued(), result.deferred(), drained, notified);
		}
	}
}
