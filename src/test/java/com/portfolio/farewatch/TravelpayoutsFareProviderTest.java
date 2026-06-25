package com.portfolio.farewatch;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.portfolio.farewatch.domain.Cabin;
import com.portfolio.farewatch.domain.TripType;
import com.portfolio.farewatch.provider.FareQuery;
import com.portfolio.farewatch.provider.FareQuote;
import com.portfolio.farewatch.provider.TravelpayoutsFareProvider;
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
 * TravelpayoutsFareProvider against a stub Aviasales Data API (JDK {@link HttpServer}, no
 * network/deps): keeps the cheapest offer whose departure falls inside the watch window
 * (an out-of-window cheaper offer is ignored), builds the source deep link, and stays a
 * no-op when disabled or missing a token.
 */
class TravelpayoutsFareProviderTest {

	private static final String PRICES_JSON = """
			{ "data": [
			  { "price": 111066, "departure_at": "2026-07-14T07:25:00+09:00", "return_at": null,
			    "link": "/search/ICN1407NRT1?x=1" },
			  { "price": 98000,  "departure_at": "2026-07-20T09:00:00+09:00", "return_at": null,
			    "link": "/search/ICN2007NRT1?x=2" },
			  { "price": 80000,  "departure_at": "2026-08-02T09:00:00+09:00", "return_at": null,
			    "link": "/search/out-of-window" }
			] }
			""";

	private HttpServer server;
	private String baseUrl;

	@BeforeEach
	void start() throws IOException {
		server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
		server.createContext("/aviasales/v3/prices_for_dates", ex -> respond(ex, PRICES_JSON));
		server.start();
		baseUrl = "http://127.0.0.1:" + server.getAddress().getPort();
	}

	@AfterEach
	void stop() {
		server.stop(0);
	}

	private FareQuery query() {
		return new FareQuery("ICN", "NRT", TripType.ONE_WAY,
				LocalDate.of(2026, 7, 1), LocalDate.of(2026, 7, 31),
				null, null, null, null, null, null, 1, Cabin.ECONOMY, "KRW");
	}

	@Test
	void returns_the_cheapest_in_window_offer() {
		TravelpayoutsFareProvider provider = new TravelpayoutsFareProvider(true, baseUrl, "tok-abc");

		Optional<FareQuote> quote = provider.cheapest(query());

		assertTrue(quote.isPresent());
		assertEquals("TRAVELPAYOUTS", quote.get().sourceCode());
		// 80000 is cheaper but departs in August (outside the July window) → excluded; 98000 wins.
		assertEquals(0, new BigDecimal("98000").compareTo(quote.get().amount()));
		assertEquals("KRW", quote.get().currency());
		assertEquals(LocalDate.of(2026, 7, 20), quote.get().departDate());
		assertTrue(quote.get().deepLink().contains("aviasales.com"));
		assertTrue(quote.get().deepLink().contains("/search/ICN2007NRT1"));
	}

	@Test
	void is_a_noop_when_disabled_or_unconfigured() {
		assertFalse(new TravelpayoutsFareProvider(false, baseUrl, "tok").cheapest(query()).isPresent());
		assertFalse(new TravelpayoutsFareProvider(true, baseUrl, "").cheapest(query()).isPresent());
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
