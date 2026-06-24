package com.portfolio.farewatch.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * Airport reference data (seeded from OurAirports). Natural key {@code iata} —
 * this is stable lookup data, not a mutable aggregate, so a surrogate UUID would
 * add nothing. Read-only from the app's side (populated by {@code AirportSeeder}).
 */
@Entity
@Table(name = "airport")
public class Airport {

	@Id
	@Column(length = 3)
	private String iata;

	@Column(nullable = false)
	private String name;

	private String municipality;

	@Column(nullable = false, length = 2)
	private String country;

	@Column(nullable = false)
	private double lat;

	@Column(nullable = false)
	private double lon;

	@Column(nullable = false)
	private boolean large;

	/** Search keywords (Korean city/airport names etc.) so "서울" matches ICN/GMP. */
	@Column(columnDefinition = "text")
	private String aliases;

	protected Airport() {
	}

	public String getIata() {
		return iata;
	}

	public String getName() {
		return name;
	}

	public String getMunicipality() {
		return municipality;
	}

	public String getCountry() {
		return country;
	}

	public double getLat() {
		return lat;
	}

	public double getLon() {
		return lon;
	}

	public boolean isLarge() {
		return large;
	}
}
