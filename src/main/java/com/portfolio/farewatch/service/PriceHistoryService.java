package com.portfolio.farewatch.service;

import com.portfolio.farewatch.domain.PricePoint;
import com.portfolio.farewatch.repo.PricePointDailyRepository;
import com.portfolio.farewatch.repo.PricePointRepository;
import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;

/**
 * The one place that answers "all-time low" correctly under retention: raw points only
 * cover the recent window ({@code farewatch.retention.raw-days}), older history lives in
 * the daily rollup — the true low is the min across both. Everything that compares
 * against the historical low (buy signal, new-low alerting) must go through here, or a
 * price that merely undercuts the RECENT window would masquerade as an all-time low.
 */
@Service
public class PriceHistoryService {

	private final PricePointRepository pricePoints;
	private final PricePointDailyRepository dailies;

	public PriceHistoryService(PricePointRepository pricePoints, PricePointDailyRepository dailies) {
		this.pricePoints = pricePoints;
		this.dailies = dailies;
	}

	/** All-time lowest amount across raw history AND the rolled-up days. */
	public Optional<BigDecimal> allTimeLowAmount(UUID watchId) {
		BigDecimal raw = pricePoints.findFirstByWatch_IdOrderByAmountAscObservedAtAsc(watchId)
				.map(PricePoint::getAmount)
				.orElse(null);
		BigDecimal rolled = dailies.lowestMin(watchId);
		if (raw == null) {
			return Optional.ofNullable(rolled);
		}
		if (rolled == null) {
			return Optional.of(raw);
		}
		return Optional.of(raw.min(rolled));
	}
}
