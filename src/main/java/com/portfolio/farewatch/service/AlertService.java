package com.portfolio.farewatch.service;

import com.portfolio.farewatch.domain.PriceAlert;
import com.portfolio.farewatch.domain.PricePoint;
import com.portfolio.farewatch.domain.Watch;
import com.portfolio.farewatch.repo.PriceAlertRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
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

	private final PriceAlertRepository alerts;
	private final NotificationService notifications;

	public AlertService(PriceAlertRepository alerts, NotificationService notifications) {
		this.alerts = alerts;
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
		PriceAlert saved = alerts.save(new PriceAlert(
				watch, lowest, watch.getAlertRule(), previousLow, lowest.getAmount(), dedupKey));
		notifications.createForAlert(saved, watch); // transactional outbox (same tx as the alert)
		return Optional.of(saved);
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
