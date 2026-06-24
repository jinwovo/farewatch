package com.portfolio.farewatch.web.dto;

import com.portfolio.farewatch.domain.Airport;

public record NearbyAirportResponse(String iata, String name, String municipality, String country, int distanceKm) {

	public static NearbyAirportResponse from(Airport a, double distanceKm) {
		return new NearbyAirportResponse(a.getIata(), a.getName(), a.getMunicipality(), a.getCountry(),
				(int) Math.round(distanceKm));
	}
}
