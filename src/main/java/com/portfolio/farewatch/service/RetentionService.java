package com.portfolio.farewatch.service;

import com.portfolio.farewatch.lock.RedisDistributedLock;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Retention for the price_point time-series — the answer to "what happens after a year?".
 * Raw points are kept for {@code raw-days} (default 90); older days are rolled up into
 * {@code price_point_daily} (min/max/avg/count per watch per UTC day) and the raw rows
 * purged, so the table stops growing linearly with time while the all-time low and
 * long-horizon stats remain answerable (via {@link PriceHistoryService}).
 *
 * <p>Two rules keep it correct:
 * <ul>
 * <li><b>Alert evidence is immortal.</b> Rows referenced by price_alert (FK) are excluded
 * from BOTH the purge and the rollup — an alert keeps its triggering row forever, and
 * excluding it from the rollup means re-runs never double-count it.</li>
 * <li><b>One UTC day per transaction.</b> Rollup (upsert merging min/max/weighted-avg)
 * and purge of a day commit together, so a crash between days loses nothing and a re-run
 * simply continues from the oldest remaining day — idempotent by construction.</li>
 * </ul>
 *
 * <p>Runs behind the same Redis lock primitive as the sweep, so N app instances do the
 * cleanup once, not N times. Each run is bounded to {@code max-days-per-run} day-slices;
 * a large backlog (e.g. first deploy of this feature) drains over a few hourly ticks.
 */
@Service
public class RetentionService {

	private static final Logger log = LoggerFactory.getLogger(RetentionService.class);
	private static final String LOCK_KEY = "farewatch:retention:lock";

	/** Rows an alert points at are exempt from rollup + purge (see class doc). */
	private static final String DELETABLE = """
			AND NOT EXISTS (SELECT 1 FROM price_alert a WHERE a.triggering_price_point_id = p.id)
			""";

	private static final String OLDEST_DELETABLE = """
			SELECT min(p.observed_at) FROM price_point p WHERE p.observed_at < ?
			""" + DELETABLE;

	// Upsert so a partially-rolled-up day (crash, alert-row later unreferenced, clock skew)
	// merges instead of conflicting: min/max fold, avg re-weighted by sample counts.
	private static final String ROLLUP_DAY = """
			INSERT INTO price_point_daily (watch_id, day, min_amount, max_amount, avg_amount, sample_count, currency)
			SELECT p.watch_id, ?, min(p.amount), max(p.amount), round(avg(p.amount), 2), count(*), min(p.currency)
			FROM price_point p
			WHERE p.observed_at >= ? AND p.observed_at < ?
			""" + DELETABLE + """
			GROUP BY p.watch_id
			ON CONFLICT (watch_id, day) DO UPDATE SET
				min_amount   = LEAST(price_point_daily.min_amount, EXCLUDED.min_amount),
				max_amount   = GREATEST(price_point_daily.max_amount, EXCLUDED.max_amount),
				avg_amount   = ROUND((price_point_daily.avg_amount * price_point_daily.sample_count
				               + EXCLUDED.avg_amount * EXCLUDED.sample_count)
				               / (price_point_daily.sample_count + EXCLUDED.sample_count), 2),
				sample_count = price_point_daily.sample_count + EXCLUDED.sample_count
			""";

	private static final String PURGE_DAY = """
			DELETE FROM price_point p WHERE p.observed_at >= ? AND p.observed_at < ?
			""" + DELETABLE;

	private final JdbcTemplate jdbc;
	private final TransactionTemplate tx;
	private final RedisDistributedLock lock;
	private final boolean enabled;
	private final int rawDays;
	private final int maxDaysPerRun;
	private final Duration lockTtl;

	public RetentionService(JdbcTemplate jdbc, PlatformTransactionManager txManager,
			RedisDistributedLock lock,
			@Value("${farewatch.retention.enabled:true}") boolean enabled,
			@Value("${farewatch.retention.raw-days:90}") int rawDays,
			@Value("${farewatch.retention.max-days-per-run:30}") int maxDaysPerRun,
			@Value("${farewatch.retention.lock-ttl-ms:600000}") long lockTtlMs) {
		this.jdbc = jdbc;
		this.tx = new TransactionTemplate(txManager);
		this.lock = lock;
		this.enabled = enabled;
		this.rawDays = Math.max(1, rawDays);
		this.maxDaysPerRun = Math.max(1, maxDaysPerRun);
		this.lockTtl = Duration.ofMillis(lockTtlMs);
	}

	@Scheduled(
			initialDelayString = "${farewatch.retention.initial-delay-ms:90000}",
			fixedDelayString = "${farewatch.retention.interval-ms:3600000}")
	public void scheduled() {
		RetentionResult r = run();
		if (r.ran() && r.daysProcessed() > 0) {
			log.info("retention: rolled up {} day(s), {} raw rows purged, {} daily rows written",
					r.daysProcessed(), r.purged(), r.rolledUp());
		}
	}

	/** Roll up + purge day-slices older than the retention window. Safe to call from any instance. */
	public RetentionResult run() {
		if (!enabled) {
			return RetentionResult.skipped();
		}
		String token = UUID.randomUUID().toString();
		if (!lock.tryLock(LOCK_KEY, token, lockTtl)) {
			return RetentionResult.skipped(); // another instance is on it
		}
		try {
			LocalDate cutoffDay = LocalDate.ofInstant(
					Instant.now().minus(Duration.ofDays(rawDays)), ZoneOffset.UTC);
			int days = 0;
			int rolledUp = 0;
			int purged = 0;
			while (days < maxDaysPerRun) {
				Timestamp oldest = jdbc.queryForObject(
						OLDEST_DELETABLE, Timestamp.class, Timestamp.from(cutoffDay.atStartOfDay(ZoneOffset.UTC).toInstant()));
				if (oldest == null) {
					break; // nothing older than the window remains
				}
				LocalDate day = LocalDate.ofInstant(oldest.toInstant(), ZoneOffset.UTC);
				int[] counts = processDay(day);
				rolledUp += counts[0];
				purged += counts[1];
				days++;
			}
			return new RetentionResult(true, days, rolledUp, purged);
		}
		finally {
			lock.unlock(LOCK_KEY, token);
		}
	}

	/** Rollup + purge of one UTC day in ONE transaction: a crash loses nothing, a re-run continues. */
	private int[] processDay(LocalDate day) {
		Timestamp from = Timestamp.from(day.atStartOfDay(ZoneOffset.UTC).toInstant());
		Timestamp to = Timestamp.from(day.plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant());
		return tx.execute(status -> {
			int upserted = jdbc.update(ROLLUP_DAY, java.sql.Date.valueOf(day), from, to);
			int deleted = jdbc.update(PURGE_DAY, from, to);
			return new int[] {upserted, deleted};
		});
	}

	public record RetentionResult(boolean ran, int daysProcessed, int rolledUp, int purged) {

		static RetentionResult skipped() {
			return new RetentionResult(false, 0, 0, 0);
		}
	}
}
