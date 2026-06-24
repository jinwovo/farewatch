package com.portfolio.farewatch.provider;

import com.portfolio.farewatch.domain.FareSource;
import com.portfolio.farewatch.ratelimit.RedisRateLimiter;
import com.portfolio.farewatch.repo.FareSourceRepository;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * Fans a query out to every <em>enabled</em> source and collects each one's
 * cheapest quote (the Skyscanner-style normalization layer). Each source call is
 * guarded by a per-source token-bucket rate limiter, so a rate-limited upstream is
 * never hammered — when a source has no tokens left, it is simply skipped this round.
 */
@Service
public class FareAggregator {

	private final List<FarePriceProvider> providers;
	private final FareSourceRepository fareSources;
	private final RedisRateLimiter rateLimiter;
	private final double ratePerSec;
	private final int burst;

	public FareAggregator(List<FarePriceProvider> providers, FareSourceRepository fareSources,
			RedisRateLimiter rateLimiter,
			@Value("${farewatch.rate-limit.per-sec:1000}") double ratePerSec,
			@Value("${farewatch.rate-limit.burst:1000}") int burst) {
		this.providers = providers;
		this.fareSources = fareSources;
		this.rateLimiter = rateLimiter;
		this.ratePerSec = ratePerSec;
		this.burst = burst;
	}

	public List<FareQuote> pollAll(FareQuery query) {
		Set<String> enabled = fareSources.findByEnabledTrue().stream()
				.map(FareSource::getCode)
				.collect(Collectors.toSet());
		List<FareQuote> quotes = new ArrayList<>();
		for (FarePriceProvider p : providers) {
			if (!enabled.contains(p.code())) {
				continue;
			}
			if (!rateLimiter.tryAcquire("src:" + p.code(), ratePerSec, burst)) {
				continue; // source rate limit hit → skip it this round
			}
			p.cheapest(query).ifPresent(quotes::add);
		}
		return quotes;
	}
}
