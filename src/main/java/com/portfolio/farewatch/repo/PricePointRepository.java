package com.portfolio.farewatch.repo;

import com.portfolio.farewatch.domain.PricePoint;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PricePointRepository extends JpaRepository<PricePoint, UUID> {

	/** Full price time-series for a watch, oldest first (for charting). */
	List<PricePoint> findByWatch_IdOrderByObservedAtAsc(UUID watchId);

	/**
	 * Most-recent N price points (newest first). Used everywhere a bounded window is enough —
	 * buy signal / anomaly stats / chart — so a watch with years of history never loads its
	 * whole series into the JVM. Pass a {@code PageRequest.of(0, N)} to cap the size.
	 */
	List<PricePoint> findByWatch_IdOrderByObservedAtDesc(UUID watchId, Pageable pageable);

	/** All-time lowest observed price for a watch. */
	Optional<PricePoint> findFirstByWatch_IdOrderByAmountAscObservedAtAsc(UUID watchId);

	/** Cheapest observed price per departure date — powers the calendar heatmap. */
	@Query("""
			select p.departDate as departDate, min(p.amount) as lowest
			from PricePoint p
			where p.watch.id = :watchId
			group by p.departDate
			order by p.departDate asc
			""")
	List<DateLow> cheapestByDepartDate(@Param("watchId") UUID watchId);

	/** Per-watch price spread, in one query — feeds the adaptive-polling volatility signal. */
	@Query("""
			select p.watch.id as watchId, min(p.amount) as min, max(p.amount) as max,
			       avg(p.amount) as avg, count(p) as cnt
			from PricePoint p
			where p.watch.id in :ids
			group by p.watch.id
			""")
	List<VolStat> volatilityStats(@Param("ids") Collection<UUID> ids);

	/**
	 * Cheapest observation per (watch, day) for MANY watches in one round-trip — powers the
	 * dashboard sparklines. Native because the day bucket is {@code date(observed_at)};
	 * aliases are quoted so PostgreSQL keeps their case for the projection. Bounded by the
	 * caller's {@code since} horizon (well inside raw retention, so raw rows cover it).
	 */
	@Query(value = """
			select p.watch_id as "watchId", date(p.observed_at) as "day", min(p.amount) as "low"
			from price_point p
			where p.watch_id in (:ids) and p.observed_at >= :since
			group by p.watch_id, date(p.observed_at)
			order by p.watch_id, date(p.observed_at)
			""", nativeQuery = true)
	List<DayLow> dayLows(@Param("ids") Collection<UUID> ids, @Param("since") Instant since);

	/** Projection for {@link #cheapestByDepartDate}. */
	interface DateLow {
		LocalDate getDepartDate();

		BigDecimal getLowest();
	}

	/** Projection for {@link #dayLows}. */
	interface DayLow {
		UUID getWatchId();

		LocalDate getDay();

		BigDecimal getLow();
	}

	/** Projection for {@link #volatilityStats}. */
	interface VolStat {
		UUID getWatchId();

		BigDecimal getMin();

		BigDecimal getMax();

		Double getAvg();

		long getCnt();
	}
}
