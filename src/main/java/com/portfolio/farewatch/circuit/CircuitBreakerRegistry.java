package com.portfolio.farewatch.circuit;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/** One {@link CircuitBreaker} per source code, created on demand with shared config. */
@Component
public class CircuitBreakerRegistry {

	private final int threshold;
	private final long cooldownMs;
	private final ConcurrentMap<String, CircuitBreaker> breakers = new ConcurrentHashMap<>();

	public CircuitBreakerRegistry(
			@Value("${farewatch.circuit-breaker.failure-threshold:5}") int threshold,
			@Value("${farewatch.circuit-breaker.cooldown-ms:30000}") long cooldownMs) {
		this.threshold = threshold;
		this.cooldownMs = cooldownMs;
	}

	public CircuitBreaker forSource(String code) {
		return breakers.computeIfAbsent(code, k -> new CircuitBreaker(threshold, cooldownMs));
	}
}
