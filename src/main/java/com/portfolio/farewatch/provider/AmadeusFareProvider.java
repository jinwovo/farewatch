package com.portfolio.farewatch.provider;

import com.portfolio.farewatch.domain.TripType;
import java.math.BigDecimal;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/**
 * Real fare source: Amadeus Self-Service (Flight Offers Search). OAuth2 client-credentials
 * token (cached until expiry) → search the route/date and keep the cheapest offer. Only
 * active when {@code farewatch.amadeus.enabled} AND a key/secret are set (env
 * {@code AMADEUS_API_KEY}/{@code AMADEUS_API_SECRET}); otherwise it's a no-op so the engine
 * runs on the simulator alone. Toggle the {@code AMADEUS} row in {@code fare_source} to
 * actually fan out to it. Defaults to the test host {@code test.api.amadeus.com}.
 */
@Component
public class AmadeusFareProvider implements FarePriceProvider {

	private static final Logger log = LoggerFactory.getLogger(AmadeusFareProvider.class);
	private static final String CODE = "AMADEUS";

	private final boolean enabled;
	private final String apiKey;
	private final String apiSecret;
	private final RestClient http;

	private volatile String token;
	private volatile Instant tokenExpiry = Instant.EPOCH;

	public AmadeusFareProvider(
			@Value("${farewatch.amadeus.enabled:false}") boolean enabled,
			@Value("${farewatch.amadeus.base-url:https://test.api.amadeus.com}") String baseUrl,
			@Value("${farewatch.amadeus.api-key:}") String apiKey,
			@Value("${farewatch.amadeus.api-secret:}") String apiSecret) {
		this.enabled = enabled;
		this.apiKey = apiKey;
		this.apiSecret = apiSecret;
		this.http = RestClient.builder().baseUrl(baseUrl).build();
	}

	@Override
	public String code() {
		return CODE;
	}

	@Override
	public Optional<FareQuote> cheapest(FareQuery q) {
		if (!isConfigured()) {
			return Optional.empty(); // not wired (no credentials) → engine runs on other sources
		}
		String tok = accessToken();
		String currency = currency(q);
		OffersResponse resp = http.get()
				.uri(b -> {
					b.path("/v2/shopping/flight-offers")
							.queryParam("originLocationCode", q.origin().toUpperCase())
							.queryParam("destinationLocationCode", q.destination().toUpperCase())
							.queryParam("departureDate", q.departDateFrom().toString())
							.queryParam("adults", Math.max(1, q.passengers()))
							.queryParam("currencyCode", currency)
							.queryParam("max", 20);
					if (q.tripType() == TripType.ROUND_TRIP && q.returnDateFrom() != null) {
						b.queryParam("returnDate", q.returnDateFrom().toString());
					}
					if (q.cabin() != null) {
						b.queryParam("travelClass", q.cabin().name());
					}
					return b.build();
				})
				.header("Authorization", "Bearer " + tok)
				.retrieve()
				.body(OffersResponse.class);

		if (resp == null || resp.data() == null || resp.data().isEmpty()) {
			return Optional.empty();
		}
		Offer best = null;
		BigDecimal bestAmount = null;
		for (Offer o : resp.data()) {
			BigDecimal amount = price(o);
			if (amount == null) {
				continue;
			}
			if (bestAmount == null || amount.compareTo(bestAmount) < 0) {
				bestAmount = amount;
				best = o;
			}
		}
		if (best == null) {
			return Optional.empty();
		}
		String cur = (best.price() != null && best.price().currency() != null) ? best.price().currency() : currency;
		LocalDate departDate = departDate(best, q.departDateFrom());
		LocalDate returnDate = q.tripType() == TripType.ROUND_TRIP ? q.returnDateFrom() : null;
		String deepLink = DeepLinks.googleFlights(q.origin(), q.destination(), departDate, returnDate);
		return Optional.of(new FareQuote(CODE, bestAmount, cur, departDate, returnDate, deepLink));
	}

	private boolean isConfigured() {
		return enabled && apiKey != null && !apiKey.isBlank() && apiSecret != null && !apiSecret.isBlank();
	}

	/** Cached OAuth2 client-credentials token; refreshed a little before expiry. */
	private String accessToken() {
		if (token != null && Instant.now().isBefore(tokenExpiry)) {
			return token;
		}
		synchronized (this) {
			if (token != null && Instant.now().isBefore(tokenExpiry)) {
				return token;
			}
			String form = "grant_type=client_credentials&client_id=" + enc(apiKey)
					+ "&client_secret=" + enc(apiSecret);
			TokenResponse tr = http.post()
					.uri("/v1/security/oauth2/token")
					.contentType(MediaType.APPLICATION_FORM_URLENCODED)
					.body(form)
					.retrieve()
					.body(TokenResponse.class);
			if (tr == null || tr.access_token() == null) {
				throw new IllegalStateException("Amadeus token request returned no access_token");
			}
			token = tr.access_token();
			long ttl = tr.expires_in() > 60 ? tr.expires_in() - 30 : Math.max(1, tr.expires_in());
			tokenExpiry = Instant.now().plusSeconds(ttl);
			return token;
		}
	}

	private static BigDecimal price(Offer o) {
		if (o == null || o.price() == null) {
			return null;
		}
		String raw = o.price().grandTotal() != null ? o.price().grandTotal() : o.price().total();
		if (raw == null) {
			return null;
		}
		try {
			return new BigDecimal(raw);
		} catch (NumberFormatException e) {
			return null;
		}
	}

	private static LocalDate departDate(Offer o, LocalDate fallback) {
		try {
			String at = o.itineraries().get(0).segments().get(0).departure().at(); // 2026-08-10T07:30:00
			return LocalDate.parse(at.substring(0, 10));
		} catch (RuntimeException e) {
			return fallback;
		}
	}

	private String currency(FareQuery q) {
		return (q.currency() == null || q.currency().isBlank()) ? "EUR" : q.currency();
	}

	private static String enc(String s) {
		return URLEncoder.encode(s, StandardCharsets.UTF_8);
	}

	// --- minimal slices of the Amadeus JSON we actually read (Jackson ignores the rest) ---
	record TokenResponse(String access_token, long expires_in) {
	}

	record OffersResponse(List<Offer> data) {
	}

	record Offer(Price price, List<Itinerary> itineraries) {
	}

	record Price(String grandTotal, String total, String currency) {
	}

	record Itinerary(List<Segment> segments) {
	}

	record Segment(Endpoint departure) {
	}

	record Endpoint(String at) {
	}
}
