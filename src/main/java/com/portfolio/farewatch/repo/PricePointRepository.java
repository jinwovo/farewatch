package com.portfolio.farewatch.repo;

import com.portfolio.farewatch.domain.PricePoint;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PricePointRepository extends JpaRepository<PricePoint, UUID> {

	/** Full price time-series for a watch, oldest first (for charting). */
	List<PricePoint> findByWatch_IdOrderByObservedAtAsc(UUID watchId);

	/** All-time lowest observed price for a watch. */
	Optional<PricePoint> findFirstByWatch_IdOrderByAmountAscObservedAtAsc(UUID watchId);
}
