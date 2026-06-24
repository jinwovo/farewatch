package com.portfolio.farewatch.weather;

import java.time.LocalDate;

/** Expected weather at the destination on a given date. */
public record WeatherEstimate(
		LocalDate date,
		Double tempMaxC,
		Double tempMinC,
		Integer precipProbPct,
		WeatherSource source) {
}
