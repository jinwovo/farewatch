package com.portfolio.farewatch.service;

import com.portfolio.farewatch.domain.Watch;
import com.portfolio.farewatch.lock.RedisDistributedLock;
import com.portfolio.farewatch.queue.SweepQueue;
import com.portfolio.farewatch.repo.PricePointRepository;
import com.portfolio.farewatch.repo.PricePointRepository.VolStat;
import com.portfolio.farewatch.repo.WatchRepository;
import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * Each tick: select the highest-value due watches (up to the per-tick budget) and
 * ENQUEUE them onto the sharded queue for the worker pool. Lock-guarded so only one
 * instance enqueues per tick (no double-enqueue); the consumer group then guarantees
 * each job is polled by exactly one worker. Adaptive: when more watches are due than
 * the budget allows, the lower-value ones are deferred and rise in score (staleness)
 * until a later tick picks them up. See {@link PollScorer}.
 */
@Service
public class FareSweepService {

	public static final String LOCK_KEY = "farewatch:lock:sweep";

	private final RedisDistributedLock lock;
	private final WatchRepository watches;
	private final PricePointRepository pricePoints;
	private final PollScorer scorer;
	private final SweepQueue queue;
	private final Duration lockTtl;
	private final int budgetPerTick;

	public FareSweepService(RedisDistributedLock lock, WatchRepository watches, PricePointRepository pricePoints,
			PollScorer scorer, SweepQueue queue,
			@Value("${farewatch.sweep.lock-ttl-ms:300000}") long lockTtlMs,
			@Value("${farewatch.sweep.budget-per-tick:100}") int budgetPerTick) {
		this.lock = lock;
		this.watches = watches;
		this.pricePoints = pricePoints;
		this.scorer = scorer;
		this.queue = queue;
		this.lockTtl = Duration.ofMillis(lockTtlMs);
		this.budgetPerTick = budgetPerTick;
	}

	public SweepResult run() {
		String token = UUID.randomUUID().toString();
		if (!lock.tryLock(LOCK_KEY, token, lockTtl)) {
			return SweepResult.notRun(); // another instance is enqueuing → no double-enqueue
		}
		try {
			List<Watch> due = watches.findByActiveTrueAndNextPollAtLessThanEqual(Instant.now());
			if (due.isEmpty()) {
				return SweepResult.ran(0, 0);
			}
			Instant now = Instant.now();
			Map<UUID, Double> volatility = volatility(due);
			due.sort(Comparator
					.comparingDouble((Watch w) -> scorer.score(w, volatility.getOrDefault(w.getId(), 0.0), now))
					.reversed());

			int enqueued = 0;
			for (Watch w : due) {
				if (enqueued >= budgetPerTick) {
					break; // budget spent — defer the rest to a later tick
				}
				queue.enqueue(w.getId());
				enqueued++;
			}
			return SweepResult.ran(enqueued, due.size() - enqueued);
		} finally {
			lock.unlock(LOCK_KEY, token);
		}
	}

	private Map<UUID, Double> volatility(List<Watch> due) {
		List<UUID> ids = due.stream().map(Watch::getId).toList();
		Map<UUID, Double> map = new HashMap<>();
		for (VolStat s : pricePoints.volatilityStats(ids)) {
			double v = (s.getAvg() != null && s.getAvg() > 0 && s.getCnt() >= 2)
					? s.getMax().subtract(s.getMin()).doubleValue() / s.getAvg()
					: 0.0;
			map.put(s.getWatchId(), v);
		}
		return map;
	}

	public record SweepResult(boolean ran, int enqueued, int deferred) {
		public static SweepResult notRun() {
			return new SweepResult(false, 0, 0);
		}

		public static SweepResult ran(int enqueued, int deferred) {
			return new SweepResult(true, enqueued, deferred);
		}
	}
}
