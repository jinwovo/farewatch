package com.portfolio.farewatch.repo;

import com.portfolio.farewatch.domain.PriceAlert;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PriceAlertRepository extends JpaRepository<PriceAlert, UUID> {

	/** Idempotency guard: has this exact trigger already fired? */
	boolean existsByDedupKey(String dedupKey);

	List<PriceAlert> findByWatch_IdOrderByCreatedAtDesc(UUID watchId);
}
