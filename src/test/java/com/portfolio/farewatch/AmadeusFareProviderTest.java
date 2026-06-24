package com.portfolio.farewatch;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.portfolio.farewatch.domain.Cabin;
import com.portfolio.farewatch.domain.TripType;
import com.portfolio.farewatch.provider.AmadeusFareProvider;
import com.portfolio.farewatch.provider.FareQuery;
import com.portfolio.farewatch.provider.FareQuote;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.OutputStream;
import java.math.BigDecimal;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * AmadeusFareProvider against a stub Amadeus (JDK {@link HttpServer}, no network/deps):
 * OAuth2 token → Flight Offers Search → cheapest offer mapping. Also asserts the provider
 * stays a no-op when disabled or missing credentials.
 */
class AmadeusFareProviderTest {

	private static final String OFFERS_JSON = """
			{ "data": [
			  { "price": { "grandTotal": "558.90", "currency": "EUR" },
			    "itineraries": [ { "segments": [ { "departure": { "at": "2026-08-10T07:30:00" } } ] } ] },
			  { "price": { "grandTotal": "412.30", "currency": "EUR" },
			    "itineraries": [ { "segments": [ { "departure": { "at": "2026-08-10T22:05:00" } } ] } ] },
			  { "price": { "grandTotal": "690.00", "currency": "EUR" },
			    "itineraries": [ { "segments": [ { "departure": { "at": "2026-08-10T13:15:00" } } ] } ] }
			] }
			""";

	private HttpServer server;
	private String baseUrl;

	@BeforeEach
	void start() throws IOException {
		server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
		server.createContext("/v1/security/oauth2/token",
				ex -> respond(ex, "{\"access_token\":\"tok-123\",\"expires_in\":1799}"));
		server.createContext("/v2/shopping/flight-offers", ex -> respond(ex, OFFERS_JSON));
		server.start();
		baseUrl = "http://127.0.0.1:" + server.getAddress().getPort();
	}

	@AfterEach
	void stop() {
		server.stop(0);
	}

	private FareQuery query() {
		return new FareQuery("ICN", "BCN", TripType.ONE_WAY,
				LocalDate.of(2026, 8, 10), LocalDate.of(2026, 8, 10),
				null, null, null, null, null, null, 1, Cabin.ECONOMY, "EUR");
	}

	@Test
	void returns_the_cheapest_offer() {
		AmadeusFareProvider provider = new AmadeusFareProvider(true, baseUrl, "key", "secret");

		Optional<FareQuote> quote = provider.cheapest(query());

		assertTrue(quote.isPresent());
		assertEquals("AMADEUS", quote.get().sourceCode());
		assertEquals(0, new BigDecimal("412.30").compareTo(quote.get().amount()));
		assertEquals("EUR", quote.get().currency());
		assertEquals(LocalDate.of(2026, 8, 10), quote.get().departDate());
		assertTrue(quote.get().deepLink().contains("ICN"));
	}

	@Test
	void is_a_noop_when_disabled_or_unconfigured() {
		assertFalse(new AmadeusFareProvider(false, baseUrl, "key", "secret").cheapest(query()).isPresent());
		assertFalse(new AmadeusFareProvider(true, baseUrl, "", "").cheapest(query()).isPresent());
	}

	private static void respond(HttpExchange ex, String body) throws IOException {
		byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
		ex.getResponseHeaders().add("Content-Type", "application/json");
		ex.sendResponseHeaders(200, bytes.length);
		try (OutputStream os = ex.getResponseBody()) {
			os.write(bytes);
		}
	}
}
