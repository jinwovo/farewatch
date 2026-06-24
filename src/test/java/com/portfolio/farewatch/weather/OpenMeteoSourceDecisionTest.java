package com.portfolio.farewatch.weather;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.time.LocalDate;
import org.junit.jupiter.api.Test;

/** Pure unit test of the forecast-vs-climate-normal horizon decision (no network). */
class OpenMeteoSourceDecisionTest {

	private final OpenMeteoWeatherProvider provider = new OpenMeteoWeatherProvider();

	@Test
	void within_16_days_uses_forecast_otherwise_climate_normal() {
		LocalDate today = LocalDate.of(2026, 6, 24);
		assertEquals(WeatherSource.FORECAST, provider.sourceFor(today, today));
		assertEquals(WeatherSource.FORECAST, provider.sourceFor(today.plusDays(10), today));
		assertEquals(WeatherSource.FORECAST, provider.sourceFor(today.plusDays(16), today));
		assertEquals(WeatherSource.CLIMATE_NORMAL, provider.sourceFor(today.plusDays(17), today));
		assertEquals(WeatherSource.CLIMATE_NORMAL, provider.sourceFor(today.plusDays(30), today)); // ~a month out
		assertEquals(WeatherSource.CLIMATE_NORMAL, provider.sourceFor(today.minusDays(1), today)); // past
	}
}
