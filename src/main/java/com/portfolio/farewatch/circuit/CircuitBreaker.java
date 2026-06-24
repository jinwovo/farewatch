package com.portfolio.farewatch.circuit;

/**
 * A small per-source circuit breaker (hand-rolled, like the Redis lock — no
 * resilience4j dependency on Boot 4, and the state machine is the point). CLOSED →
 * trips to OPEN after {@code threshold} consecutive failures → after {@code cooldown}
 * it allows one HALF_OPEN probe → a successful probe CLOSES it, a failed probe
 * re-OPENS it. While OPEN, the aggregator skips the source entirely (fast fail).
 */
public class CircuitBreaker {

	public enum State {
		CLOSED, OPEN, HALF_OPEN
	}

	private final int threshold;
	private final long cooldownMs;

	private State state = State.CLOSED;
	private int failures = 0;
	private long openedAt = 0;

	public CircuitBreaker(int threshold, long cooldownMs) {
		this.threshold = threshold;
		this.cooldownMs = cooldownMs;
	}

	/** May this call go through? (Transitions OPEN → HALF_OPEN once the cooldown elapses.) */
	public synchronized boolean permit() {
		if (state == State.OPEN) {
			if (System.currentTimeMillis() - openedAt >= cooldownMs) {
				state = State.HALF_OPEN;
				return true; // allow a single probe
			}
			return false;
		}
		return true; // CLOSED or HALF_OPEN
	}

	public synchronized void recordSuccess() {
		failures = 0;
		state = State.CLOSED;
	}

	public synchronized void recordFailure() {
		if (state == State.HALF_OPEN || ++failures >= threshold) {
			state = State.OPEN;
			openedAt = System.currentTimeMillis();
			failures = 0;
		}
	}

	public synchronized State state() {
		return state;
	}
}
