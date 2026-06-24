package com.portfolio.farewatch.weather;

import java.time.LocalDate;

/** Looks up the weather estimate for a coordinate + date. Returns null on failure. */
public interface WeatherProvider {

	WeatherEstimate estimate(double lat, double lon, LocalDate date);
}
