package com.portfolio.farewatch.web.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/**
 * One home-dashboard card: the watch plus everything the list page needs to render it
 * live (latest observed price, buy signal, 30-day sparkline) — served in a single call
 * so the home page never fans out N per-watch requests.
 */
public record WatchSummaryResponse(
		WatchResponse watch,
		BigDecimal latestAmount,
		Instant latestObservedAt,
		BuySignal signal,
		List<Cell> spark) {

	/** One sparkline day: the cheapest observation of that day. */
	public record Cell(LocalDate day, BigDecimal amount) {
	}
}
