package com.portfolio.farewatch.web.dto;

/**
 * "Buy now or wait" decision signal for a watch — the product's headline value:
 * turn a raw price stream into a recommendation. Transparent stats (no ML):
 * where the current price sits in the route's own history (percentile), which way
 * it's trending, how bouncy it is, and how close departure is.
 */
public record BuySignal(
		String recommendation, // BUY / WAIT / CONSIDER / NO_DATA
		int score, // 0–100, higher = better to buy now
		double currentAmount,
		double lowestAmount,
		double percentile, // 0 = cheapest ever seen, 100 = most expensive
		double trendPct, // recent vs prior window, % (>0 rising)
		double volatilityPct, // coefficient of variation, %
		long daysToDeparture,
		String reason) {
}
