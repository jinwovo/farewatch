package com.portfolio.farewatch.repo;

import com.portfolio.farewatch.domain.FareSource;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface FareSourceRepository extends JpaRepository<FareSource, UUID> {

	Optional<FareSource> findByCode(String code);

	List<FareSource> findByEnabledTrue();
}
