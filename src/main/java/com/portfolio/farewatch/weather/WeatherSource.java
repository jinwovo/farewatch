package com.portfolio.farewatch.weather;

/**
 * Where a day's weather estimate came from. A trip a month out is beyond the ~16-day
 * forecast horizon, so it uses CLIMATE_NORMAL (historical average for that calendar
 * day); once the date enters the horizon it switches to a real FORECAST.
 */
public enum WeatherSource {
	FORECAST,
	CLIMATE_NORMAL
}
