package com.portfolio.farewatch.scheduler;

import com.portfolio.farewatch.notify.NotificationDispatcher;
import com.portfolio.farewatch.service.FareSweepService;
import com.portfolio.farewatch.service.FareSweepService.SweepResult;
import com.portfolio.farewatch.worker.PollWorker;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Duration;
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
	private final MeterRegistry metrics;
	private final int drainMax;
	private final Duration reclaimMinIdle;
	private final int maxDeliveries;

	public FareSweepScheduler(FareSweepService sweep, PollWorker worker, NotificationDispatcher dispatcher,
			MeterRegistry metrics,
			@Value("${farewatch.sweep.budget-per-tick:100}") int budgetPerTick,
			@Value("${farewatch.sweep.reclaim-min-idle-ms:120000}") long reclaimMinIdleMs,
			@Value("${farewatch.sweep.max-deliveries:5}") int maxDeliveries) {
		this.sweep = sweep;
		this.worker = worker;
		this.dispatcher = dispatcher;
		this.metrics = metrics;
		this.drainMax = Math.max(1, budgetPerTick) * 2;
		this.reclaimMinIdle = Duration.ofMillis(reclaimMinIdleMs);
		this.maxDeliveries = Math.max(1, maxDeliveries);
	}

	@Scheduled(
			initialDelayString = "${farewatch.sweep.initial-delay-ms:3600000}",
			fixedDelayString = "${farewatch.sweep.interval-ms:3600000}")
	public void scheduled() {
		SweepResult result = sweep.run();
		int recovered = worker.recover(reclaimMinIdle, maxDeliveries, drainMax); // take over jobs from dead workers
		int drained = worker.drain(drainMax);
		int notified = dispatcher.dispatch(drainMax * 2);
		metrics.counter("farewatch.sweep.enqueued").increment(result.enqueued());
		metrics.counter("farewatch.sweep.deferred").increment(result.deferred());
		metrics.counter("farewatch.sweep.recovered").increment(recovered);
		metrics.counter("farewatch.sweep.drained").increment(drained);
		if (result.ran() || recovered > 0 || drained > 0 || notified > 0) {
			log.info("sweep: enqueued {} (deferred {}), recovered {}, drained {}, notified {}",
					result.enqueued(), result.deferred(), recovered, drained, notified);
		}
	}
}
