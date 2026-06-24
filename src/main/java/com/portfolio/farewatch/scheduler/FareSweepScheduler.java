package com.portfolio.farewatch.scheduler;

import com.portfolio.farewatch.service.FareSweepService;
import com.portfolio.farewatch.service.FareSweepService.SweepResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Fires the sweep on a fixed cadence (hourly by default). Multiple app instances
 * all run this trigger; {@link FareSweepService} dedupes them via the Redis lock,
 * so only one actually polls in any given window.
 */
@Component
public class FareSweepScheduler {

	private static final Logger log = LoggerFactory.getLogger(FareSweepScheduler.class);

	private final FareSweepService sweep;

	public FareSweepScheduler(FareSweepService sweep) {
		this.sweep = sweep;
	}

	@Scheduled(
			initialDelayString = "${farewatch.sweep.initial-delay-ms:3600000}",
			fixedDelayString = "${farewatch.sweep.interval-ms:3600000}")
	public void scheduled() {
		SweepResult result = sweep.run();
		if (result.ran()) {
			log.info("sweep ran: polled {} due watch(es)", result.polled());
		} else {
			log.debug("sweep skipped: another instance holds the lock");
		}
	}
}
