package com.portfolio.farewatch.service;

import com.portfolio.farewatch.domain.Watch;
import com.portfolio.farewatch.lock.RedisDistributedLock;
import com.portfolio.farewatch.repo.WatchRepository;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * One sweep over all due watches, guarded by a Redis distributed lock. If another
 * instance already holds the lock we return {@link SweepResult#skipped()} WITHOUT
 * polling — that is the "no double-poll across N instances" guarantee. Each due
 * watch is polled in its own transaction (via {@link PollService#poll}).
 */
@Service
public class FareSweepService {

	public static final String LOCK_KEY = "farewatch:lock:sweep";

	private final RedisDistributedLock lock;
	private final WatchRepository watches;
	private final PollService pollService;
	private final Duration lockTtl;

	public FareSweepService(RedisDistributedLock lock, WatchRepository watches, PollService pollService,
			@Value("${farewatch.sweep.lock-ttl-ms:300000}") long lockTtlMs) {
		this.lock = lock;
		this.watches = watches;
		this.pollService = pollService;
		this.lockTtl = Duration.ofMillis(lockTtlMs);
	}

	public SweepResult run() {
		String token = UUID.randomUUID().toString();
		if (!lock.tryLock(LOCK_KEY, token, lockTtl)) {
			return SweepResult.skipped(); // another instance is sweeping → do NOT double-poll
		}
		try {
			List<Watch> due = watches.findByActiveTrueAndNextPollAtLessThanEqual(Instant.now());
			for (Watch w : due) {
				pollService.poll(w.getId());
			}
			return SweepResult.ran(due.size());
		} finally {
			lock.unlock(LOCK_KEY, token);
		}
	}

	public record SweepResult(boolean ran, int polled) {
		public static SweepResult skipped() {
			return new SweepResult(false, 0);
		}

		public static SweepResult ran(int polled) {
			return new SweepResult(true, polled);
		}
	}
}
