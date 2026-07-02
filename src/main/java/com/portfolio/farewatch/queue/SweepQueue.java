package com.portfolio.farewatch.queue;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.annotation.PostConstruct;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.data.domain.Range;
import org.springframework.data.redis.connection.RedisStreamCommands.XClaimOptions;
import org.springframework.data.redis.connection.stream.Consumer;
import org.springframework.data.redis.connection.stream.PendingMessage;
import org.springframework.data.redis.connection.stream.PendingMessages;
import org.springframework.data.redis.connection.stream.ReadOffset;
import org.springframework.data.redis.connection.stream.RecordId;
import org.springframework.data.redis.connection.stream.StreamOffset;
import org.springframework.data.redis.connection.stream.StreamReadOptions;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

/**
 * The sharded poll queue, backed by a Redis Stream + consumer group. The sweep
 * (one instance, lock-guarded) enqueues due watches; any number of worker
 * instances read from the <em>same</em> consumer group, so each enqueued job is
 * delivered to exactly one worker — that is the horizontal-scale story. Acked
 * jobs are done; unacked jobs stay pending for reclaim if a worker dies.
 */
@Component
public class SweepQueue {

	static final String STREAM = "farewatch:sweep:stream";
	static final String GROUP = "pollers";
	/** Where jobs that fail too many times are parked for inspection instead of looping forever. */
	static final String DLQ = "farewatch:sweep:dlq";

	private final StringRedisTemplate redis;
	private final long streamMaxLen;

	public SweepQueue(StringRedisTemplate redis, MeterRegistry metrics,
			@Value("${farewatch.sweep.stream-maxlen:50000}") long streamMaxLen) {
		this.redis = redis;
		this.streamMaxLen = streamMaxLen;
		// queue depth + parked-forever jobs, sampled from Redis at scrape time
		Gauge.builder("farewatch.queue.depth", this, SweepQueue::streamSize).register(metrics);
		Gauge.builder("farewatch.queue.dlq", this, SweepQueue::deadLetterSize).register(metrics);
	}

	@PostConstruct
	void ensureGroup() {
		try {
			redis.opsForStream().createGroup(STREAM, ReadOffset.from("0"), GROUP);
		} catch (Exception e) {
			// group already exists (BUSYGROUP) — fine
		}
	}

	/**
	 * Producer: append a watch to the stream, then bound the stream's length with an approximate
	 * XTRIM (~). Trimming is safe here because enqueue is self-healing: a watch stays due until a
	 * poll advances its {@code next_poll_at}, so a job trimmed before it was processed is simply
	 * re-enqueued on the next sweep tick. The bound must stay well above peak in-flight depth.
	 */
	public RecordId enqueue(UUID watchId) {
		RecordId id = redis.opsForStream().add(STREAM, Map.of("watchId", watchId.toString()));
		redis.opsForStream().trim(STREAM, streamMaxLen, true); // approximate (~) → amortized O(1)
		return id;
	}

	/** Current stream length (for monitoring / tests). */
	public long streamSize() {
		Long n = redis.opsForStream().size(STREAM);
		return n == null ? 0L : n;
	}

	/** Consumer: claim up to {@code count} not-yet-delivered jobs for this consumer. */
	public List<Job> read(String consumer, int count) {
		var records = redis.opsForStream().read(
				Consumer.from(GROUP, consumer),
				StreamReadOptions.empty().count(count),
				StreamOffset.create(STREAM, ReadOffset.lastConsumed()));
		List<Job> jobs = new ArrayList<>();
		if (records != null) {
			for (var r : records) {
				Object watchId = r.getValue().get("watchId");
				jobs.add(new Job(r.getId().getValue(), UUID.fromString(String.valueOf(watchId))));
			}
		}
		return jobs;
	}

	/** Mark a job done so it leaves the pending list. */
	public void ack(String recordId) {
		redis.opsForStream().acknowledge(STREAM, GROUP, recordId);
	}

	/**
	 * Crash recovery (the XAUTOCLAIM pattern). Scans the group's pending list for jobs
	 * a now-dead worker claimed but never acked (idle longer than {@code minIdle}). Each
	 * such job is either:
	 * <ul>
	 *   <li><b>dead-lettered</b> — if it has already been delivered {@code maxDeliveries}
	 *       times and still failed, its payload is parked on the {@link #DLQ} stream and it
	 *       is acked off the main group (so it stops looping forever), or</li>
	 *   <li><b>reclaimed</b> — XCLAIM transfers ownership to {@code consumer}, and the job is
	 *       returned for a retry.</li>
	 * </ul>
	 * The delivery count comes from XPENDING (before the claim), which is why this is built
	 * from XPENDING + XCLAIM rather than a bare XAUTOCLAIM: XAUTOCLAIM alone can't make the
	 * give-up decision.
	 */
	public List<Job> reclaim(String consumer, Duration minIdle, int maxDeliveries, int count) {
		PendingMessages pending = redis.opsForStream().pending(STREAM, GROUP, Range.unbounded(), count);
		if (pending == null || pending.isEmpty()) {
			return List.of();
		}
		// id -> delivery count, for jobs idle long enough to assume their owner is gone
		Map<RecordId, Long> stale = new LinkedHashMap<>();
		for (PendingMessage pm : pending) {
			if (pm.getElapsedTimeSinceLastDelivery().compareTo(minIdle) >= 0) {
				stale.put(pm.getId(), pm.getTotalDeliveryCount());
			}
		}
		if (stale.isEmpty()) {
			return List.of();
		}
		RecordId[] ids = stale.keySet().toArray(new RecordId[0]);
		var claimed = redis.opsForStream().claim(STREAM, GROUP, consumer, XClaimOptions.minIdle(minIdle).ids(ids));
		List<Job> retry = new ArrayList<>();
		if (claimed != null) {
			for (var r : claimed) {
				long deliveries = stale.getOrDefault(r.getId(), 1L);
				if (deliveries >= maxDeliveries) {
					redis.opsForStream().add(DLQ, r.getValue()); // park the payload for inspection
					redis.opsForStream().acknowledge(STREAM, GROUP, r.getId().getValue()); // stop redelivering
				} else {
					retry.add(new Job(r.getId().getValue(), UUID.fromString(String.valueOf(r.getValue().get("watchId")))));
				}
			}
		}
		return retry;
	}

	/** Number of jobs that have been given up on (for monitoring / the chaos test). */
	public long deadLetterSize() {
		Long n = redis.opsForStream().size(DLQ);
		return n == null ? 0L : n;
	}

	public record Job(String recordId, UUID watchId) {
	}
}
