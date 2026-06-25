package com.portfolio.farewatch.service;

import com.portfolio.farewatch.domain.PriceAlert;
import com.portfolio.farewatch.domain.PricePoint;
import com.portfolio.farewatch.domain.Watch;
import com.portfolio.farewatch.repo.PriceAlertRepository;
import com.portfolio.farewatch.repo.PricePointRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Service;

/**
 * Change detection. Given the watch's new lowest price and its previous low,
 * decide whether the watch's alert rule fired, and if so persist an idempotent
 * {@link PriceAlert} (deduplicated by a stable key). Runs inside the caller's
 * transaction (the poll).
 */
@Service
public class AlertService {

	private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);

	/** Minimum prior observations before a z-score is statistically meaningful. */
	private static final int ANOMALY_MIN_HISTORY = 8;
	/** A new low this many standard deviations below the route's mean is flagged a mistake fare. */
	private static final double ANOMALY_Z = -2.5;

	private final PriceAlertRepository alerts;
	private final PricePointRepository pricePoints;
	private final NotificationService notifications;

	public AlertService(PriceAlertRepository alerts, PricePointRepository pricePoints,
			NotificationService notifications) {
		this.alerts = alerts;
		this.pricePoints = pricePoints;
		this.notifications = notifications;
	}

	public Optional<PriceAlert> evaluate(Watch watch, PricePoint lowest, BigDecimal previousLow) {
		if (lowest == null || !triggered(watch, lowest.getAmount(), previousLow)) {
			return Optional.empty();
		}
		String dedupKey = watch.getId() + ":" + watch.getAlertRule() + ":"
				+ lowest.getAmount().stripTrailingZeros().toPlainString() + ":" + lowest.getDepartDate();
		if (alerts.existsByDedupKey(dedupKey)) {
			return Optional.empty(); // already fired — idempotent
		}
		PriceAlert alert = new PriceAlert(
				watch, lowest, watch.getAlertRule(), previousLow, lowest.getAmount(), dedupKey);
		alert.setMistakeFare(isAnomaly(watch, lowest.getAmount()));
		PriceAlert saved = alerts.save(alert);
		notifications.createForAlert(saved, watch); // transactional outbox (same tx as the alert)
		return Optional.of(saved);
	}

	/**
	 * Mistake-fare detector. A genuine fare error (pricing-engine bug, fat-finger, flash drop)
	 * lands far below the route's own recent prices. We model the watch's price history as a
	 * normal distribution and flag the new low when it sits at least {@code ANOMALY_Z} standard
	 * deviations below the mean — purely from the numbers, no thresholds to tune per route.
	 */
	private boolean isAnomaly(Watch watch, BigDecimal newLow) {
		List<PricePoint> history = pricePoints.findByWatch_IdOrderByObservedAtAsc(watch.getId());
		if (history.size() < ANOMALY_MIN_HISTORY) {
			return false; // not enough signal to call anything an outlier yet
		}
		double n = history.size();
		double mean = history.stream().mapToDouble(p -> p.getAmount().doubleValue()).sum() / n;
		double variance = history.stream()
				.mapToDouble(p -> Math.pow(p.getAmount().doubleValue() - mean, 2)).sum() / n;
		double sd = Math.sqrt(variance);
		if (sd <= 0) {
			return false; // flat history — no spread to be an outlier against
		}
		double z = (newLow.doubleValue() - mean) / sd;
		return z <= ANOMALY_Z;
	}

	private boolean triggered(Watch w, BigDecimal now, BigDecimal previousLow) {
		return switch (w.getAlertRule()) {
			case NEW_LOW -> previousLow == null || now.compareTo(previousLow) < 0;
			case BELOW_THRESHOLD -> w.getThresholdAmount() != null && now.compareTo(w.getThresholdAmount()) <= 0;
			case DROP_PCT -> previousLow != null && w.getDropPct() != null && previousLow.signum() > 0
					&& previousLow.subtract(now).multiply(HUNDRED).divide(previousLow, 2, RoundingMode.HALF_UP)
							.compareTo(w.getDropPct()) >= 0;
		};
	}
}
