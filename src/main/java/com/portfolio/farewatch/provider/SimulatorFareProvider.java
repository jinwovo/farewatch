package com.portfolio.farewatch.provider;

import com.portfolio.farewatch.domain.TripType;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Optional;
import java.util.concurrent.ThreadLocalRandom;
import org.springframework.stereotype.Component;

/**
 * Always-on synthetic source. A stable per-route baseline plus a seasonal /
 * day-of-week shape, a departure time-of-day factor, and fresh per-poll jitter —
 * so a watch's series drifts and occasionally sets a new low, and choosing a
 * different time window (e.g. red-eye vs evening peak) actually moves the price.
 */
@Component
public class SimulatorFareProvider implements FarePriceProvider {

	private static final String CODE = "SIMULATOR";

	@Override
	public String code() {
		return CODE;
	}

	@Override
	public Optional<FareQuote> cheapest(FareQuery q) {
		String currency = (q.currency() == null || q.currency().isBlank()) ? "KRW" : q.currency();
		long routeSeed = Math.abs((q.origin().toUpperCase() + "-" + q.destination().toUpperCase()).hashCode());
		double base = (90_000 + (routeSeed % 760_000)) * timeWindowFactor(q.departTimeFrom(), q.departTimeTo());

		// Walk the flexible departure window; keep the cheapest day.
		LocalDate bestDepart = q.departDateFrom();
		double bestOutbound = Double.MAX_VALUE;
		for (LocalDate d = q.departDateFrom(); !d.isAfter(q.departDateTo()); d = d.plusDays(1)) {
			double leg = legPrice(base, d);
			if (leg < bestOutbound) {
				bestOutbound = leg;
				bestDepart = d;
			}
		}

		double total = bestOutbound;
		LocalDate bestReturn = null;
		if (q.tripType() == TripType.ROUND_TRIP && q.returnDateFrom() != null) {
			LocalDate rTo = q.returnDateTo() != null ? q.returnDateTo() : q.returnDateFrom();
			double bestInbound = Double.MAX_VALUE;
			for (LocalDate d = q.returnDateFrom(); !d.isAfter(rTo); d = d.plusDays(1)) {
				double leg = legPrice(base * 0.96, d);
				if (leg < bestInbound) {
					bestInbound = leg;
					bestReturn = d;
				}
			}
			total += bestInbound;
		}

		int pax = Math.max(1, q.passengers());
		long amount = Math.round(total * pax / 100.0) * 100; // round to nearest 100
		String deepLink = "https://book.simulator.example/?o=" + q.origin().toUpperCase()
				+ "&d=" + q.destination().toUpperCase() + "&date=" + bestDepart
				+ (bestReturn != null ? "&ret=" + bestReturn : "");
		return Optional.of(new FareQuote(CODE, BigDecimal.valueOf(amount), currency, bestDepart, bestReturn, deepLink));
	}

	private double legPrice(double base, LocalDate date) {
		double weekend = (date.getDayOfWeek().getValue() >= 6) ? 1.18 : 1.0;
		double seasonal = 1.0 + 0.15 * Math.sin(date.getDayOfYear() / 58.0);
		double jitter = 1.0 + (ThreadLocalRandom.current().nextDouble() - 0.5) * 0.20; // +/-10% per poll
		return base * weekend * seasonal * jitter;
	}

	/** Departure time-of-day pricing: red-eye cheaper, commuter/evening peaks pricier. */
	private double timeWindowFactor(LocalTime from, LocalTime to) {
		if (from == null || to == null) {
			return 1.0;
		}
		int midHour = ((from.toSecondOfDay() + to.toSecondOfDay()) / 2) / 3600;
		if (midHour < 6) {
			return 0.90; // red-eye / dawn
		}
		if (midHour < 9) {
			return 1.06; // morning peak
		}
		if (midHour < 17) {
			return 1.00; // midday
		}
		if (midHour < 21) {
			return 1.08; // evening peak
		}
		return 0.95; // late night
	}
}
