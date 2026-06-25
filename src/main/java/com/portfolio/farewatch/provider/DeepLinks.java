package com.portfolio.farewatch.provider;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;

/**
 * Builds the "최저가 사이트로 이동" target. Since there is no public booking API, every source
 * deep-links to a real Google Flights search pre-filled with the route + date(s) — a working
 * link the user can act on (same model as Skyscanner: search, don't book here).
 */
final class DeepLinks {

	private DeepLinks() {
	}

	static String googleFlights(String origin, String dest, LocalDate depart, LocalDate ret) {
		String q = "Flights from " + origin.toUpperCase() + " to " + dest.toUpperCase() + " on " + depart
				+ (ret != null ? " returning " + ret : "");
		return "https://www.google.com/travel/flights?q=" + URLEncoder.encode(q, StandardCharsets.UTF_8);
	}
}
