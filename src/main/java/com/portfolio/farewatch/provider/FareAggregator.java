package com.portfolio.farewatch.provider;

import com.portfolio.farewatch.domain.FareSource;
import com.portfolio.farewatch.repo.FareSourceRepository;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;

/**
 * Fans a query out to every <em>enabled</em> source and collects each one's
 * cheapest quote (this is the Skyscanner-style normalization layer). Per-source
 * rate limiting / circuit breaking / health fallback land here in P4; for now it
 * just respects the {@code fare_source.enabled} flag.
 */
@Service
public class FareAggregator {

	private final List<FarePriceProvider> providers;
	private final FareSourceRepository fareSources;

	public FareAggregator(List<FarePriceProvider> providers, FareSourceRepository fareSources) {
		this.providers = providers;
		this.fareSources = fareSources;
	}

	public List<FareQuote> pollAll(FareQuery query) {
		Set<String> enabled = fareSources.findByEnabledTrue().stream()
				.map(FareSource::getCode)
				.collect(Collectors.toSet());
		List<FareQuote> quotes = new ArrayList<>();
		for (FarePriceProvider p : providers) {
			if (enabled.contains(p.code())) {
				p.cheapest(query).ifPresent(quotes::add);
			}
		}
		return quotes;
	}
}
