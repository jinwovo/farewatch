package com.portfolio.farewatch.weather;

import com.portfolio.farewatch.domain.Airport;
import com.portfolio.farewatch.domain.Watch;
import com.portfolio.farewatch.repo.AirportRepository;
import com.portfolio.farewatch.repo.WatchRepository;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.NoSuchElementException;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import org.springframework.stereotype.Service;

/**
 * Destination weather for a watch: the expected temperature/precip on each day of the
 * (flexible) departure window, at the destination airport's coordinates. Results are
 * cached per coordinate+date so we don't re-hit Open-Meteo for an unchanged lookup.
 */
@Service
public class WeatherService {

	private static final int MAX_DAYS = 5;

	private final WatchRepository watches;
	private final AirportRepository airports;
	private final WeatherProvider provider;
	private final ConcurrentMap<String, WeatherEstimate> cache = new ConcurrentHashMap<>();

	public WeatherService(WatchRepository watches, AirportRepository airports, WeatherProvider provider) {
		this.watches = watches;
		this.airports = airports;
		this.provider = provider;
	}

	public List<WeatherEstimate> forWatch(UUID watchId) {
		Watch w = watches.findById(watchId)
				.orElseThrow(() -> new NoSuchElementException("watch not found: " + watchId));
		Airport destination = airports.findById(w.getDestination()).orElse(null);
		if (destination == null) {
			return List.of();
		}
		List<WeatherEstimate> out = new ArrayList<>();
		LocalDate date = w.getDepartDateFrom();
		int count = 0;
		while (!date.isAfter(w.getDepartDateTo()) && count < MAX_DAYS) {
			WeatherEstimate estimate = cached(destination.getLat(), destination.getLon(), date);
			if (estimate != null) {
				out.add(estimate);
			}
			date = date.plusDays(1);
			count++;
		}
		return out;
	}

	private WeatherEstimate cached(double lat, double lon, LocalDate date) {
		String key = String.format(Locale.US, "%.2f,%.2f,%s", lat, lon, date);
		return cache.computeIfAbsent(key, k -> provider.estimate(lat, lon, date));
	}
}
