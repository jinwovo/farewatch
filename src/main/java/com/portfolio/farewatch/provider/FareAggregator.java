package com.portfolio.farewatch.provider;

import com.portfolio.farewatch.circuit.CircuitBreaker;
import com.portfolio.farewatch.circuit.CircuitBreakerRegistry;
import com.portfolio.farewatch.domain.FareSource;
import com.portfolio.farewatch.ratelimit.RedisRateLimiter;
import com.portfolio.farewatch.repo.FareSourceRepository;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
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
	private final MeterRegistry metrics;
	private final double ratePerSec;
	private final int burst;

	public FareAggregator(List<FarePriceProvider> providers, FareSourceRepository fareSources,
			RedisRateLimiter rateLimiter, CircuitBreakerRegistry breakers, MeterRegistry metrics,
			@Value("${farewatch.rate-limit.per-sec:1000}") double ratePerSec,
			@Value("${farewatch.rate-limit.burst:1000}") int burst) {
		this.providers = providers;
		this.fareSources = fareSources;
		this.rateLimiter = rateLimiter;
		this.breakers = breakers;
		this.metrics = metrics;
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
				skips(p.code(), "circuit_open");
				continue; // circuit open → skip this source (fast fail)
			}
			if (!rateLimiter.tryAcquire("src:" + p.code(), ratePerSec, burst)) {
				skips(p.code(), "rate_limited");
				continue; // source rate limit hit → skip it this round
			}
			Timer.Sample sample = Timer.start(metrics);
			try {
				boolean hasQuote = p.cheapest(query).map(quotes::add).orElse(false);
				breaker.recordSuccess();
				calls(sample, p.code(), hasQuote ? "quote" : "empty");
			} catch (RuntimeException e) {
				breaker.recordFailure();
				calls(sample, p.code(), "error");
			}
		}
		return quotes;
	}

	private void calls(Timer.Sample sample, String source, String outcome) {
		sample.stop(metrics.timer("farewatch.source.calls", "source", source, "outcome", outcome));
	}

	private void skips(String source, String reason) {
		metrics.counter("farewatch.source.skips", "source", source, "reason", reason).increment();
	}
}
