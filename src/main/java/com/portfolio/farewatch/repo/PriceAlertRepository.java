package com.portfolio.farewatch.repo;

import com.portfolio.farewatch.domain.PriceAlert;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface PriceAlertRepository extends JpaRepository<PriceAlert, UUID> {

	/** Idempotency guard: has this exact trigger already fired? */
	boolean existsByDedupKey(String dedupKey);

	List<PriceAlert> findByWatch_IdOrderByCreatedAtDesc(UUID watchId);

	/**
	 * Global feed: newest alerts across ALL watches, with watch + triggering point
	 * fetched up front (both are to-one, so fetch-join paginates fine in SQL).
	 */
	@Query("""
			select a from PriceAlert a
			join fetch a.watch
			join fetch a.triggeringPricePoint
			order by a.createdAt desc
			""")
	List<PriceAlert> recentWithContext(Pageable pageable);
}
