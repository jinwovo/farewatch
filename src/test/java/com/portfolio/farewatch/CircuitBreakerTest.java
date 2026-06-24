package com.portfolio.farewatch;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.portfolio.farewatch.circuit.CircuitBreaker;
import com.portfolio.farewatch.circuit.CircuitBreaker.State;
import org.junit.jupiter.api.Test;

/** Plain unit test of the circuit-breaker state machine (no Spring context). */
class CircuitBreakerTest {

	@Test
	void opens_after_threshold_failures_then_blocks_during_cooldown() {
		CircuitBreaker cb = new CircuitBreaker(3, 10_000);
		assertTrue(cb.permit());
		cb.recordFailure();
		cb.recordFailure();
		assertTrue(cb.permit());           // 2 < 3 → still closed
		cb.recordFailure();                // 3rd → open
		assertEquals(State.OPEN, cb.state());
		assertFalse(cb.permit());          // within cooldown → blocked
	}

	@Test
	void half_open_probe_recovers_or_reopens() {
		CircuitBreaker cb = new CircuitBreaker(2, 0); // cooldown 0 → immediate half-open
		cb.recordFailure();
		cb.recordFailure();                // open
		assertTrue(cb.permit());           // cooldown elapsed → half-open probe allowed
		cb.recordSuccess();                // probe ok → closed
		assertEquals(State.CLOSED, cb.state());

		cb.recordFailure();
		cb.recordFailure();                // open again
		assertTrue(cb.permit());           // half-open
		cb.recordFailure();                // probe fails → re-open
		assertEquals(State.OPEN, cb.state());
	}
}
