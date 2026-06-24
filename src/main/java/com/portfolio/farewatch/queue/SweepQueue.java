package com.portfolio.farewatch.queue;

import jakarta.annotation.PostConstruct;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.data.redis.connection.stream.Consumer;
import org.springframework.data.redis.connection.stream.ReadOffset;
import org.springframework.data.redis.connection.stream.RecordId;
import org.springframework.data.redis.connection.stream.StreamOffset;
import org.springframework.data.redis.connection.stream.StreamReadOptions;
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

	private final StringRedisTemplate redis;

	public SweepQueue(StringRedisTemplate redis) {
		this.redis = redis;
	}

	@PostConstruct
	void ensureGroup() {
		try {
			redis.opsForStream().createGroup(STREAM, ReadOffset.from("0"), GROUP);
		} catch (Exception e) {
			// group already exists (BUSYGROUP) — fine
		}
	}

	/** Producer: append a watch to the stream. */
	public RecordId enqueue(UUID watchId) {
		return redis.opsForStream().add(STREAM, Map.of("watchId", watchId.toString()));
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

	public record Job(String recordId, UUID watchId) {
	}
}
