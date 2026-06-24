package com.portfolio.farewatch.web;

import com.portfolio.farewatch.domain.Airport;
import com.portfolio.farewatch.repo.AirportRepository;
import com.portfolio.farewatch.web.dto.AirportResponse;
import com.portfolio.farewatch.web.dto.NearbyAirportResponse;
import java.util.List;
import java.util.NoSuchElementException;
import org.springframework.data.domain.PageRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Skyscanner-style location autocomplete: search airports + nearby airports. */
@RestController
@RequestMapping("/api/airports")
public class AirportController {

	private final AirportRepository airports;

	public AirportController(AirportRepository airports) {
		this.airports = airports;
	}

	@GetMapping
	public List<AirportResponse> search(@RequestParam String q, @RequestParam(defaultValue = "8") int limit) {
		if (q == null || q.isBlank()) {
			return List.of();
		}
		int capped = Math.min(Math.max(limit, 1), 20);
		return airports.search(q.trim(), PageRequest.of(0, capped)).stream()
				.map(AirportResponse::from)
				.toList();
	}

	@GetMapping("/{iata}/nearby")
	public List<NearbyAirportResponse> nearby(@PathVariable String iata,
			@RequestParam(defaultValue = "5") int limit) {
		Airport origin = airports.findById(iata.toUpperCase())
				.orElseThrow(() -> new NoSuchElementException("airport not found: " + iata));
		int capped = Math.min(Math.max(limit, 1), 10);
		return airports.nearby(origin.getLat(), origin.getLon(), origin.getIata(), PageRequest.of(0, capped)).stream()
				.map(a -> NearbyAirportResponse.from(a, haversineKm(origin, a)))
				.toList();
	}

	private static double haversineKm(Airport a, Airport b) {
		double dLat = Math.toRadians(b.getLat() - a.getLat());
		double dLon = Math.toRadians(b.getLon() - a.getLon());
		double h = Math.sin(dLat / 2) * Math.sin(dLat / 2)
				+ Math.cos(Math.toRadians(a.getLat())) * Math.cos(Math.toRadians(b.getLat()))
						* Math.sin(dLon / 2) * Math.sin(dLon / 2);
		return 6371.0 * 2 * Math.atan2(Math.sqrt(h), Math.sqrt(1 - h));
	}
}
