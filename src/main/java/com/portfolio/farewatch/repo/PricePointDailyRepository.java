package com.portfolio.farewatch.repo;

import com.portfolio.farewatch.domain.PricePointDaily;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PricePointDailyRepository extends JpaRepository<PricePointDaily, UUID> {

	/**
	 * Lowest price ever seen in the ROLLED-UP history (null if nothing rolled up yet).
	 * The true all-time low is {@code min(this, raw low)} — see PriceHistoryService.
	 */
	@Query("select min(d.minAmount) from PricePointDaily d where d.watch.id = :watchId")
	BigDecimal lowestMin(@Param("watchId") UUID watchId);

	List<PricePointDaily> findByWatch_IdOrderByDayAsc(UUID watchId);
}
