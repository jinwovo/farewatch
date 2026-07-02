package com.portfolio.farewatch;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.portfolio.farewatch.domain.AlertRule;
import com.portfolio.farewatch.domain.FareSource;
import com.portfolio.farewatch.domain.PriceAlert;
import com.portfolio.farewatch.domain.PricePoint;
import com.portfolio.farewatch.domain.PricePointDaily;
import com.portfolio.farewatch.domain.TripType;
import com.portfolio.farewatch.domain.Watch;
import com.portfolio.farewatch.repo.FareSourceRepository;
import com.portfolio.farewatch.repo.PriceAlertRepository;
import com.portfolio.farewatch.repo.PricePointDailyRepository;
import com.portfolio.farewatch.repo.PricePointRepository;
import com.portfolio.farewatch.repo.WatchRepository;
import com.portfolio.farewatch.service.PriceHistoryService;
import com.portfolio.farewatch.service.RetentionService;
import com.portfolio.farewatch.service.RetentionService.RetentionResult;
import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

/**
 * Retention: raw price points older than the window are rolled up into one row per
 * (watch, UTC day) and purged; rows an alert references survive untouched (and are
 * NOT double-counted into the rollup); the all-time low keeps its true value across
 * the raw/rolled-up boundary; a re-run is a no-op (idempotent).
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@Testcontainers
class RetentionIntegrationTest {

	@Container
	static GenericContainer<?> redis = new GenericContainer<>(DockerImageName.parse("redis:7-alpine"))
			.withExposedPorts(6379);

	@DynamicPropertySource
	static void redisProperties(DynamicPropertyRegistry registry) {
		registry.add("spring.data.redis.host", redis::getHost);
		registry.add("spring.data.redis.port", () -> redis.getMappedPort(6379));
	}

	@Autowired
	RetentionService retention;
	@Autowired
	PriceHistoryService priceHistory;
	@Autowired
	WatchRepository watches;
	@Autowired
	PricePointRepository pricePoints;
	@Autowired
	PricePointDailyRepository dailies;
	@Autowired
	PriceAlertRepository alerts;
	@Autowired
	FareSourceRepository sources;
	@Autowired
	JdbcTemplate jdbc;

	@Test
	void old_days_roll_up_and_purge_alert_evidence_survives_rerun_is_noop() {
		Watch w = watches.save(watch("ICN", "NRT"));
		FareSource sim = sources.findByCode("SIMULATOR").orElseThrow();
		Instant oldDay = Instant.now().minus(Duration.ofDays(100));
		Instant alertDay = Instant.now().minus(Duration.ofDays(95));

		// 3 points on one old day → expect min 80 / max 120 / avg 100 / count 3 after rollup
		UUID p1 = backdatedPoint(w, sim, "100.00", oldDay);
		UUID p2 = backdatedPoint(w, sim, "80.00", oldDay.plus(Duration.ofHours(2)));
		UUID p3 = backdatedPoint(w, sim, "120.00", oldDay.plus(Duration.ofHours(4)));
		// an old point an alert references → immortal, never rolled up
		UUID pAlert = backdatedPoint(w, sim, "90.00", alertDay);
		PricePoint alertPoint = pricePoints.findById(pAlert).orElseThrow();
		alerts.save(new PriceAlert(w, alertPoint, AlertRule.NEW_LOW,
				new BigDecimal("100.00"), new BigDecimal("90.00"), "retention-test-" + w.getId()));
		// a recent point inside the raw window → untouched
		UUID pRecent = backdatedPoint(w, sim, "150.00", Instant.now());

		RetentionResult result = retention.run();
		assertTrue(result.ran());
		assertEquals(1, result.daysProcessed(), "only the 3-point day is deletable (alert day is exempt)");
		assertEquals(3, result.purged());

		// raw: the rolled-up day is gone, alert evidence + recent survive
		List<UUID> rawIds = pricePoints.findByWatch_IdOrderByObservedAtAsc(w.getId())
				.stream().map(PricePoint::getId).toList();
		assertEquals(List.of(pAlert, pRecent), rawIds);
		assertTrue(pricePoints.findById(p1).isEmpty() && pricePoints.findById(p2).isEmpty()
				&& pricePoints.findById(p3).isEmpty());

		// rollup: one daily row carrying min/max/avg/count of the purged day
		List<PricePointDaily> days = dailies.findByWatch_IdOrderByDayAsc(w.getId());
		assertEquals(1, days.size());
		PricePointDaily d = days.get(0);
		assertEquals(0, d.getMinAmount().compareTo(new BigDecimal("80.00")));
		assertEquals(0, d.getMaxAmount().compareTo(new BigDecimal("120.00")));
		assertEquals(0, d.getAvgAmount().compareTo(new BigDecimal("100.00")));
		assertEquals(3, d.getSampleCount());

		// all-time low crosses the raw/rolled-up boundary: raw min is 90, true low is the rolled-up 80
		assertEquals(0, priceHistory.allTimeLowAmount(w.getId()).orElseThrow()
				.compareTo(new BigDecimal("80.00")));

		// idempotent: nothing older than the window remains → re-run is a no-op
		RetentionResult again = retention.run();
		assertEquals(0, again.daysProcessed());
		assertEquals(1, dailies.findByWatch_IdOrderByDayAsc(w.getId()).size());
	}

	/** Save a point, then backdate observed_at (CreationTimestamp is not settable in code). */
	private UUID backdatedPoint(Watch w, FareSource src, String amount, Instant observedAt) {
		PricePoint p = pricePoints.save(new PricePoint(
				w, src, new BigDecimal(amount), "KRW", w.getDepartDateFrom(), null, "https://example.test"));
		jdbc.update("UPDATE price_point SET observed_at = ? WHERE id = ?",
				Timestamp.from(observedAt), p.getId());
		return p.getId();
	}

	private Watch watch(String origin, String dest) {
		Watch w = new Watch();
		w.setUserRef("retention-test");
		w.setOrigin(origin);
		w.setDestination(dest);
		w.setTripType(TripType.ONE_WAY);
		w.setDepartDateFrom(LocalDate.now().plusDays(30));
		w.setDepartDateTo(LocalDate.now().plusDays(30));
		w.setNextPollAt(Instant.now().plus(Duration.ofDays(1)));
		return w;
	}
}
