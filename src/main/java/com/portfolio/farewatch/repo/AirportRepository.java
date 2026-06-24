package com.portfolio.farewatch.repo;

import com.portfolio.farewatch.domain.Airport;
import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AirportRepository extends JpaRepository<Airport, String> {

	/** Autocomplete: exact IATA, or case-insensitive prefix on city / name (word starts too). */
	@Query("""
			select a from Airport a
			where upper(a.iata) = upper(:q)
			   or lower(a.name) like lower(concat(:q, '%'))
			   or lower(a.municipality) like lower(concat(:q, '%'))
			   or lower(a.name) like lower(concat('% ', :q, '%'))
			order by case when upper(a.iata) = upper(:q) then 0 else 1 end, a.large desc, a.name asc
			""")
	List<Airport> search(@Param("q") String q, Pageable pageable);

	/** Nearest other airports by great-circle distance (haversine via SQL). */
	@Query(value = """
			select a.* from airport a
			where a.iata <> :iata
			order by 6371 * acos(least(1.0, greatest(-1.0,
			    cos(radians(:lat)) * cos(radians(a.lat)) * cos(radians(a.lon) - radians(:lon))
			    + sin(radians(:lat)) * sin(radians(a.lat)))))
			""", nativeQuery = true)
	List<Airport> nearby(@Param("lat") double lat, @Param("lon") double lon, @Param("iata") String iata,
			Pageable pageable);
}
