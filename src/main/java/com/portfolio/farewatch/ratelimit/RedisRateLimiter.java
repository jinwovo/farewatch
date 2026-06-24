package com.portfolio.farewatch.ratelimit;

import java.util.List;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Component;

/**
 * Distributed token-bucket rate limiter (Redis + Lua). Refill and consume happen
 * atomically in one script using the Redis server clock, so it's correct across
 * instances. Used to protect each rate-limited fare source from being hammered by
 * the sweep. Fails OPEN: if the limiter store is unreachable, it allows the call
 * rather than blocking all polling.
 */
@Component
public class RedisRateLimiter {

	// KEYS[1] bucket; ARGV[1] rate/sec, ARGV[2] capacity(burst), ARGV[3] requested.
	private static final String LUA = """
			local rate = tonumber(ARGV[1])
			local cap = tonumber(ARGV[2])
			local req = tonumber(ARGV[3])
			local t = redis.call('TIME')
			local now = tonumber(t[1]) * 1000 + (tonumber(t[2]) / 1000)
			local cur = redis.call('HMGET', KEYS[1], 'tokens', 'ts')
			local tokens = tonumber(cur[1])
			local ts = tonumber(cur[2])
			if tokens == nil then tokens = cap end
			if ts == nil then ts = now end
			local elapsed = now - ts
			if elapsed < 0 then elapsed = 0 end
			tokens = math.min(cap, tokens + (elapsed / 1000.0) * rate)
			local allowed = 0
			if tokens >= req then tokens = tokens - req; allowed = 1 end
			redis.call('HSET', KEYS[1], 'tokens', tokens, 'ts', now)
			redis.call('EXPIRE', KEYS[1], math.ceil(cap / math.max(rate, 0.0001)) + 2)
			return allowed
			""";

	private final StringRedisTemplate redis;
	private final RedisScript<Long> script;

	public RedisRateLimiter(StringRedisTemplate redis) {
		this.redis = redis;
		this.script = new DefaultRedisScript<>(LUA, Long.class);
	}

	/** Try to consume one token from {@code key}'s bucket (rate/sec, capacity=burst). */
	public boolean tryAcquire(String key, double ratePerSec, int burst) {
		try {
			Long allowed = redis.execute(script, List.of("rl:" + key),
					Double.toString(ratePerSec), Integer.toString(burst), "1");
			return allowed == null || allowed == 1L;
		} catch (RuntimeException e) {
			return true; // fail-open: never let a limiter outage stop all polling
		}
	}
}
