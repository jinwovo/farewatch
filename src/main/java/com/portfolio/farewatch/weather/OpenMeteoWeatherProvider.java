package com.portfolio.farewatch.weather;

import java.time.DateTimeException;
import java.time.LocalDate;
import java.util.List;
import java.util.Locale;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/**
 * Weather from Open-Meteo (free, no API key). Within the forecast horizon it calls
 * the forecast API; beyond it (e.g. a trip a month out) it builds a CLIMATE_NORMAL by
 * averaging the same calendar day over the last few years from the historical archive.
 * Fails soft (returns null) so a weather outage never breaks the page.
 */
@Component
public class OpenMeteoWeatherProvider implements WeatherProvider {

	private static final Logger log = LoggerFactory.getLogger(OpenMeteoWeatherProvider.class);
	private static final int FORECAST_HORIZON_DAYS = 16;
	private static final int NORMAL_YEARS = 3;

	private final RestClient http = RestClient.create();

	@Override
	public WeatherEstimate estimate(double lat, double lon, LocalDate date) {
		try {
			return sourceFor(date, LocalDate.now()) == WeatherSource.FORECAST
					? forecast(lat, lon, date)
					: climateNormal(lat, lon, date);
		} catch (RuntimeException e) {
			log.warn("weather lookup failed ({}, {}, {}): {}", lat, lon, date, e.toString());
			return null;
		}
	}

	/** Forecast if the date is within the next {@value #FORECAST_HORIZON_DAYS} days, else a climate normal. */
	WeatherSource sourceFor(LocalDate date, LocalDate today) {
		if (!date.isBefore(today) && !date.isAfter(today.plusDays(FORECAST_HORIZON_DAYS))) {
			return WeatherSource.FORECAST;
		}
		return WeatherSource.CLIMATE_NORMAL;
	}

	private WeatherEstimate forecast(double lat, double lon, LocalDate date) {
		String url = String.format(Locale.US,
				"https://api.open-meteo.com/v1/forecast?latitude=%.4f&longitude=%.4f"
						+ "&daily=temperature_2m_max,temperature_2m_min,precipitation_probability_max"
						+ "&timezone=auto&start_date=%s&end_date=%s",
				lat, lon, date, date);
		Daily d = daily(url);
		if (d == null) {
			return null;
		}
		return new WeatherEstimate(date, round(first(d.temperature_2m_max())), round(first(d.temperature_2m_min())),
				first(d.precipitation_probability_max()), WeatherSource.FORECAST);
	}

	private WeatherEstimate climateNormal(double lat, double lon, LocalDate date) {
		double sumMax = 0, sumMin = 0;
		int rainyYears = 0, n = 0;
		int thisYear = LocalDate.now().getYear();
		for (int y = thisYear - NORMAL_YEARS; y < thisYear; y++) {
			LocalDate day = sameDay(date, y);
			String url = String.format(Locale.US,
					"https://archive-api.open-meteo.com/v1/archive?latitude=%.4f&longitude=%.4f"
							+ "&daily=temperature_2m_max,temperature_2m_min,precipitation_sum"
							+ "&timezone=auto&start_date=%s&end_date=%s",
					lat, lon, day, day);
			Daily d = daily(url);
			Double max = d == null ? null : first(d.temperature_2m_max());
			Double min = d == null ? null : first(d.temperature_2m_min());
			if (max == null || min == null) {
				continue;
			}
			sumMax += max;
			sumMin += min;
			Double precip = first(d.precipitation_sum());
			if (precip != null && precip >= 1.0) {
				rainyYears++;
			}
			n++;
		}
		if (n == 0) {
			return null;
		}
		int precipProb = (int) Math.round(100.0 * rainyYears / n);
		return new WeatherEstimate(date, round(sumMax / n), round(sumMin / n), precipProb, WeatherSource.CLIMATE_NORMAL);
	}

	private Daily daily(String url) {
		OpenMeteoResponse r = http.get().uri(url).retrieve().body(OpenMeteoResponse.class);
		if (r == null || r.daily() == null || r.daily().time() == null || r.daily().time().isEmpty()) {
			return null;
		}
		return r.daily();
	}

	private static LocalDate sameDay(LocalDate date, int year) {
		try {
			return date.withYear(year);
		} catch (DateTimeException e) {
			return date.minusDays(1).withYear(year); // Feb 29 → Feb 28
		}
	}

	private static <T> T first(List<T> list) {
		return (list == null || list.isEmpty()) ? null : list.get(0);
	}

	private static Double round(Double v) {
		return v == null ? null : Math.round(v * 10.0) / 10.0;
	}

	record OpenMeteoResponse(Daily daily) {
	}

	record Daily(
			List<String> time,
			List<Double> temperature_2m_max,
			List<Double> temperature_2m_min,
			List<Integer> precipitation_probability_max,
			List<Double> precipitation_sum) {
	}
}
