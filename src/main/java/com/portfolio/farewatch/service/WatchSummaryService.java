package com.portfolio.farewatch.service;

import com.portfolio.farewatch.domain.Airport;
import com.portfolio.farewatch.domain.PricePoint;
import com.portfolio.farewatch.domain.Watch;
import com.portfolio.farewatch.repo.AirportRepository;
import com.portfolio.farewatch.repo.PricePointRepository;
import com.portfolio.farewatch.web.dto.WatchResponse;
import com.portfolio.farewatch.web.dto.WatchSummaryResponse;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Builds the home-dashboard payload: every watch enriched with its latest price, buy
 * signal and a 30-day sparkline. Reads stay bounded — one batched day-min query for all
 * sparklines, one batched airport lookup, and per watch the same capped stat window the
 * buy signal already uses — so cost scales with the user's list, not with history length.
 */
@Service
public class WatchSummaryService {

	/** Sparkline horizon — well inside raw retention, so raw day-mins cover it fully. */
	private static final int SPARK_DAYS = 30;

	private final WatchService watchService;
	private final PricePointRepository pricePoints;
	private final BuySignalService buySignals;
	private final AirportRepository airports;

	public WatchSummaryService(WatchService watchService, PricePointRepository pricePoints,
			BuySignalService buySignals, AirportRepository airports) {
		this.watchService = watchService;
		this.pricePoints = pricePoints;
		this.buySignals = buySignals;
		this.airports = airports;
	}

	@Transactional(readOnly = true)
	public List<WatchSummaryResponse> summaries(String userRef) {
		List<Watch> ws = new ArrayList<>(watchService.list(userRef));
		if (ws.isEmpty()) {
			return List.of();
		}
		ws.sort(Comparator.comparing(Watch::getCreatedAt,
				Comparator.nullsLast(Comparator.reverseOrder())));

		List<UUID> ids = ws.stream().map(Watch::getId).toList();
		Instant since = Instant.now().minus(SPARK_DAYS, ChronoUnit.DAYS);
		Map<UUID, List<WatchSummaryResponse.Cell>> sparks = new HashMap<>();
		for (PricePointRepository.DayLow d : pricePoints.dayLows(ids, since)) {
			sparks.computeIfAbsent(d.getWatchId(), k -> new ArrayList<>())
					.add(new WatchSummaryResponse.Cell(d.getDay(), d.getLow()));
		}

		Set<String> codes = ws.stream()
				.flatMap(w -> Stream.of(w.getOrigin(), w.getDestination()))
				.collect(Collectors.toSet());
		Map<String, Airport> byIata = new HashMap<>();
		airports.findAllById(codes).forEach(a -> byIata.put(a.getIata(), a));

		List<WatchSummaryResponse> out = new ArrayList<>(ws.size());
		for (Watch w : ws) {
			List<PricePoint> pts = pricePoints
					.findByWatch_IdOrderByObservedAtDesc(w.getId(),
							PageRequest.of(0, BuySignalService.STAT_WINDOW))
					.reversed();
			PricePoint latest = pts.isEmpty() ? null : pts.get(pts.size() - 1);
			out.add(new WatchSummaryResponse(
					WatchResponse.from(w, byIata.get(w.getOrigin()), byIata.get(w.getDestination())),
					latest == null ? null : latest.getAmount(),
					latest == null ? null : latest.getObservedAt(),
					buySignals.signalFor(w, pts),
					sparks.getOrDefault(w.getId(), List.of())));
		}
		return out;
	}
}
