package com.portfolio.farewatch.provider;

import com.portfolio.farewatch.circuit.CircuitBreaker;
import com.portfolio.farewatch.circuit.CircuitBreakerRegistry;
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
 * Fans a query out to every <em>enabled</em> source and collects each one's cheapest
 * quote (the Skyscanner-style normalization layer). Each source call is protected by
 * a circuit breaker (a failing source is skipped while its circuit is open) and a
 * per-source token-bucket rate limiter (a throttled source is skipped this round) —
 * so one slow or broken upstream never drags the whole poll down.
 */
@Service
public class FareAggregator {

	private final List<FarePriceProvider> providers;
	private final FareSourceRepository fareSources;
	private final RedisRateLimiter rateLimiter;
	private final CircuitBreakerRegistry breakers;
	private final double ratePerSec;
	private final int burst;

	public FareAggregator(List<FarePriceProvider> providers, FareSourceRepository fareSources,
			RedisRateLimiter rateLimiter, CircuitBreakerRegistry breakers,
			@Value("${farewatch.rate-limit.per-sec:1000}") double ratePerSec,
			@Value("${farewatch.rate-limit.burst:1000}") int burst) {
		this.providers = providers;
		this.fareSources = fareSources;
		this.rateLimiter = rateLimiter;
		this.breakers = breakers;
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
			CircuitBreaker breaker = breakers.forSource(p.code());
			if (!breaker.permit()) {
				continue; // circuit open → skip this source (fast fail)
			}
			if (!rateLimiter.tryAcquire("src:" + p.code(), ratePerSec, burst)) {
				continue; // source rate limit hit → skip it this round
			}
			try {
				p.cheapest(query).ifPresent(quotes::add);
				breaker.recordSuccess();
			} catch (RuntimeException e) {
				breaker.recordFailure();
			}
		}
		return quotes;
	}
}
