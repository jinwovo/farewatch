package com.portfolio.farewatch.web.dto;

import com.portfolio.farewatch.domain.Airport;

public record AirportResponse(String iata, String name, String municipality, String country, boolean large, String korean) {

	public static AirportResponse from(Airport a) {
		return new AirportResponse(a.getIata(), a.getName(), a.getMunicipality(), a.getCountry(), a.isLarge(), a.getKorean());
	}
}
