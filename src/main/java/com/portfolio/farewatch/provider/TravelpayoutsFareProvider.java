package com.portfolio.farewatch.provider;

import com.portfolio.farewatch.domain.TripType;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/**
 * Real fare source: Travelpayouts (Aviasales) Flight Data API — cached cheapest fares built
 * from real Aviasales searches (no live-search approval needed, generous free limits, so it
 * fits an always-on watch better than a metered live API). Queries {@code prices_for_dates}
 * for the route, keeps the cheapest offer whose departure (and return, for round trips) falls
 * inside the watch's flexible window. Only active when {@code farewatch.travelpayouts.enabled}
 * AND a token is set (env {@code TRAVELPAYOUTS_TOKEN}); otherwise a no-op so the engine runs on
 * the other sources. Toggle the {@code TRAVELPAYOUTS} row in {@code fare_source} to fan out to it.
 */
@Component
public class TravelpayoutsFareProvider implements FarePriceProvider {

	private static final String CODE = "TRAVELPAYOUTS";
	private static final String AVIASALES = "https://www.aviasales.com";

	private final boolean enabled;
	private final String token;
	private final RestClient http;

	public TravelpayoutsFareProvider(
			@Value("${farewatch.travelpayouts.enabled:false}") boolean enabled,
			@Value("${farewatch.travelpayouts.base-url:https://api.travelpayouts.com}") String baseUrl,
			@Value("${farewatch.travelpayouts.token:}") String token) {
		this.enabled = enabled;
		this.token = token;
		this.http = RestClient.builder().baseUrl(baseUrl).build();
	}

	@Override
	public String code() {
		return CODE;
	}

	@Override
	public Optional<FareQuote> cheapest(FareQuery q) {
		if (!isConfigured()) {
			return Optional.empty(); // not wired (no token) → engine runs on other sources
		}
		String currency = (q.currency() == null || q.currency().isBlank()) ? "krw" : q.currency().toLowerCase();
		boolean roundTrip = q.tripType() == TripType.ROUND_TRIP && q.returnDateFrom() != null;

		PricesResponse resp = http.get()
				.uri(b -> {
					b.path("/aviasales/v3/prices_for_dates")
							.queryParam("origin", q.origin().toUpperCase())
							.queryParam("destination", q.destination().toUpperCase())
							.queryParam("departure_at", month(q.departDateFrom()))
							.queryParam("currency", currency)
							.queryParam("sorting", "price")
							.queryParam("one_way", !roundTrip)
							.queryParam("limit", 100)
							.queryParam("token", token);
					if (roundTrip) {
						b.queryParam("return_at", month(q.returnDateFrom()));
					}
					return b.build();
				})
				.retrieve()
				.body(PricesResponse.class);

		if (resp == null || resp.data() == null || resp.data().isEmpty()) {
			return Optional.empty();
		}

		Offer best = null;
		for (Offer o : resp.data()) {
			if (o.price() == null || o.departure_at() == null) {
				continue;
			}
			LocalDate dep = date(o.departure_at());
			if (dep == null || dep.isBefore(q.departDateFrom()) || dep.isAfter(q.departDateTo())) {
				continue; // outside the watch's flexible departure window
			}
			if (roundTrip && !returnInWindow(o, q)) {
				continue;
			}
			if (best == null || o.price() < best.price()) {
				best = o;
			}
		}
		if (best == null) {
			return Optional.empty();
		}

		LocalDate departDate = date(best.departure_at());
		LocalDate returnDate = roundTrip ? date(best.return_at()) : null;
		String deepLink = (best.link() != null && !best.link().isBlank())
				? AVIASALES + best.link()
				: DeepLinks.googleFlights(q.origin(), q.destination(), departDate, returnDate);
		return Optional.of(new FareQuote(
				CODE, BigDecimal.valueOf(Math.round(best.price())), currency.toUpperCase(),
				departDate, returnDate, deepLink));
	}

	private boolean isConfigured() {
		return enabled && token != null && !token.isBlank();
	}

	private boolean returnInWindow(Offer o, FareQuery q) {
		LocalDate ret = date(o.return_at());
		if (ret == null) {
			return false;
		}
		LocalDate to = q.returnDateTo() != null ? q.returnDateTo() : q.returnDateFrom();
		return !ret.isBefore(q.returnDateFrom()) && !ret.isAfter(to);
	}

	/** Travelpayouts wants the month as {@code yyyy-MM}; it returns all dates in that month. */
	private static String month(LocalDate d) {
		return d.toString().substring(0, 7);
	}

	/** Parse the date part of an ISO datetime like {@code 2026-07-14T07:25:00+09:00}. */
	private static LocalDate date(String isoDateTime) {
		if (isoDateTime == null || isoDateTime.length() < 10) {
			return null;
		}
		try {
			return LocalDate.parse(isoDateTime.substring(0, 10));
		} catch (RuntimeException e) {
			return null;
		}
	}

	// --- minimal slices of the Travelpayouts JSON we actually read (Jackson ignores the rest) ---
	record PricesResponse(List<Offer> data) {
	}

	record Offer(Double price, String departure_at, String return_at, String link) {
	}
}
