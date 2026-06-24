package com.portfolio.farewatch.service;

import com.portfolio.farewatch.domain.FareSource;
import com.portfolio.farewatch.domain.PricePoint;
import com.portfolio.farewatch.domain.Watch;
import com.portfolio.farewatch.provider.FareAggregator;
import com.portfolio.farewatch.provider.FareQuery;
import com.portfolio.farewatch.provider.FareQuote;
import com.portfolio.farewatch.repo.FareSourceRepository;
import com.portfolio.farewatch.repo.PricePointRepository;
import com.portfolio.farewatch.repo.WatchRepository;
import com.portfolio.farewatch.web.dto.PollResultResponse;
import com.portfolio.farewatch.web.dto.PricePointResponse;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Polls one watch across all enabled sources, appends a price point per quote to
 * the time-series, advances the watch's poll schedule, and reports whether this
 * poll set a new low. In P2 the sweep calls this; for now it is also exposed
 * via POST /api/watches/{id}/poll for manual triggering and tests.
 */
@Service
public class PollService {

	private final WatchRepository watches;
	private final PricePointRepository pricePoints;
	private final FareSourceRepository fareSources;
	private final FareAggregator aggregator;

	public PollService(WatchRepository watches, PricePointRepository pricePoints,
			FareSourceRepository fareSources, FareAggregator aggregator) {
		this.watches = watches;
		this.pricePoints = pricePoints;
		this.fareSources = fareSources;
		this.aggregator = aggregator;
	}

	@Transactional
	public PollResultResponse poll(UUID watchId) {
		Watch w = watches.findById(watchId)
				.orElseThrow(() -> new NoSuchElementException("watch not found: " + watchId));

		BigDecimal lowestBefore = pricePoints
				.findFirstByWatch_IdOrderByAmountAscObservedAtAsc(watchId)
				.map(PricePoint::getAmount)
				.orElse(null);

		List<FareQuote> quotes = aggregator.pollAll(FareQuery.from(w));
		List<PricePoint> saved = new ArrayList<>();
		for (FareQuote q : quotes) {
			FareSource source = fareSources.findByCode(q.sourceCode())
					.orElseThrow(() -> new IllegalStateException("unknown source: " + q.sourceCode()));
			saved.add(pricePoints.save(new PricePoint(
					w, source, q.amount(), q.currency(), q.departDate(), q.returnDate(), q.deepLink())));
		}

		Instant now = Instant.now();
		w.setLastPolledAt(now);
		w.setNextPollAt(now.plus(Duration.ofMinutes(w.getPollIntervalMin())));
		watches.save(w);

		PricePoint lowest = pricePoints
				.findFirstByWatch_IdOrderByAmountAscObservedAtAsc(watchId)
				.orElse(null);
		boolean newLow = lowest != null
				&& (lowestBefore == null || lowest.getAmount().compareTo(lowestBefore) < 0);

		List<PricePointResponse> newPrices = saved.stream().map(PricePointResponse::from).toList();
		return new PollResultResponse(
				w.getId(), now, newPrices,
				lowest != null ? lowest.getAmount() : null,
				lowest != null ? lowest.getCurrency() : null,
				lowest != null ? lowest.getDepartDate() : null,
				lowest != null ? lowest.getDeepLink() : null,
				newLow);
	}
}
