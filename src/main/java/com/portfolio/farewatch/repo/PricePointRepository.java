package com.portfolio.farewatch.repo;

import com.portfolio.farewatch.domain.PricePoint;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PricePointRepository extends JpaRepository<PricePoint, UUID> {

	/** Full price time-series for a watch, oldest first (for charting). */
	List<PricePoint> findByWatch_IdOrderByObservedAtAsc(UUID watchId);

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

	/** Projection for {@link #cheapestByDepartDate}. */
	interface DateLow {
		LocalDate getDepartDate();

		BigDecimal getLowest();
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
