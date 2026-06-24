package com.portfolio.farewatch.provider;

import java.util.Optional;

/**
 * One price source. Implementations are the only place that knows how to talk to
 * a given upstream (Amadeus, Travelpayouts, an LCC scraper, the simulator); the
 * rest of the engine (scheduling, change detection, alerting) is source-agnostic.
 * {@link #code()} must match a {@code fare_source.code} row so it can be toggled.
 */
public interface FarePriceProvider {

	String code();

	Optional<FareQuote> cheapest(FareQuery query);
}
