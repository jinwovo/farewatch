package com.portfolio.farewatch.repo;

import com.portfolio.farewatch.domain.Watch;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface WatchRepository extends JpaRepository<Watch, UUID> {

	List<Watch> findByUserRefOrderByCreatedAtDesc(String userRef);

	/** Due-for-poll scan used by the P2 sweep (next_poll_at <= cutoff, active only). */
	List<Watch> findByActiveTrueAndNextPollAtLessThanEqual(Instant cutoff);

	/**
	 * Bounded due-scan for the sweep: oldest-due first (the most starved), capped by the
	 * {@link Pageable} limit, so a huge backlog never loads every due row into the JVM. The
	 * partial index {@code idx_watch_due (next_poll_at) WHERE active} serves this directly.
	 */
	List<Watch> findByActiveTrueAndNextPollAtLessThanEqualOrderByNextPollAtAsc(Instant cutoff, Pageable pageable);
}
