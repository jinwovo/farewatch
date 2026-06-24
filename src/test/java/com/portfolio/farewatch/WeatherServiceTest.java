package com.portfolio.farewatch;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.portfolio.farewatch.domain.TripType;
import com.portfolio.farewatch.domain.Watch;
import com.portfolio.farewatch.repo.AirportRepository;
import com.portfolio.farewatch.repo.WatchRepository;
import com.portfolio.farewatch.weather.WeatherEstimate;
import com.portfolio.farewatch.weather.WeatherProvider;
import com.portfolio.farewatch.weather.WeatherService;
import com.portfolio.farewatch.weather.WeatherSource;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

/**
 * WeatherService: one estimate per day of the (flexible) departure window at the
 * destination airport's coordinates, with per-coordinate+date caching. Uses a fake
 * provider so the test is deterministic and offline.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
class WeatherServiceTest {

	@Autowired
	WatchRepository watches;
	@Autowired
	AirportRepository airports;

	@Test
	void returns_one_estimate_per_day_and_caches() {
		// 3-day flexible window to HKG (seeded airport → has coordinates)
		Watch w = watches.save(watch("ICN", "HKG", LocalDate.now().plusDays(40), LocalDate.now().plusDays(42)));
		CountingProvider provider = new CountingProvider();
		WeatherService service = new WeatherService(watches, airports, provider);

		List<WeatherEstimate> first = service.forWatch(w.getId());
		assertEquals(3, first.size());
		assertEquals(3, provider.calls.get()); // one lookup per day

		List<WeatherEstimate> second = service.forWatch(w.getId());
		assertEquals(3, second.size());
		assertEquals(3, provider.calls.get()); // cached → no extra lookups
	}

	private Watch watch(String origin, String dest, LocalDate from, LocalDate to) {
		Watch w = new Watch();
		w.setUserRef("weather-" + UUID.randomUUID());
		w.setOrigin(origin);
		w.setDestination(dest);
		w.setTripType(TripType.ONE_WAY);
		w.setDepartDateFrom(from);
		w.setDepartDateTo(to);
		w.setNextPollAt(Instant.now());
		return w;
	}

	static class CountingProvider implements WeatherProvider {
		final AtomicInteger calls = new AtomicInteger();

		@Override
		public WeatherEstimate estimate(double lat, double lon, LocalDate date) {
			calls.incrementAndGet();
			return new WeatherEstimate(date, 28.0, 22.0, 30, WeatherSource.CLIMATE_NORMAL);
		}
	}
}
