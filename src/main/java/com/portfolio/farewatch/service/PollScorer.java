package com.portfolio.farewatch.service;

import com.portfolio.farewatch.domain.Watch;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import org.springframework.stereotype.Component;

/**
 * Adaptive-polling value model. With far more watches than the per-tick API budget,
 * the sweep can't poll everything — so it polls the watches most worth a look right
 * now. A watch scores higher when its departure is near (the user cares more), when
 * it's overdue relative to its interval (fairness / freshness), and when its recent
 * price history is volatile (the price is actually moving). This is the domain's
 * scheduling-under-a-budget problem.
 */
@Component
public class PollScorer {

	private static final double W_PROXIMITY = 1.0;
	private static final double W_STALENESS = 0.8;
	private static final double W_VOLATILITY = 1.2;

	/** @param volatility (max-min)/avg over the watch's observed prices (0 if too few). */
	public double score(Watch w, double volatility, Instant now) {
		long daysToDepart = Math.max(0,
				ChronoUnit.DAYS.between(now.atZone(ZoneOffset.UTC).toLocalDate(), w.getDepartDateFrom()));
		double proximity = 1.0 / (1.0 + daysToDepart / 7.0); // ~1 if imminent, decays ~weekly

		double minutesStale = w.getLastPolledAt() == null
				? w.getPollIntervalMin() * 2.0
				: Math.max(0, ChronoUnit.MINUTES.between(w.getLastPolledAt(), now));
		double staleness = Math.min(2.0, minutesStale / Math.max(1, w.getPollIntervalMin()));

		double vol = Math.min(1.0, Math.max(0.0, volatility));

		return W_PROXIMITY * proximity + W_STALENESS * staleness + W_VOLATILITY * vol;
	}
}
