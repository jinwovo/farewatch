package com.portfolio.farewatch.lock;

import java.time.Duration;
import java.util.List;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Component;

/**
 * A correct single-instance-of-Redis distributed lock.
 *
 * <p>Acquire = {@code SET key token NX PX ttl} (atomic, fenced by a unique token).
 * Release = a Lua compare-and-delete so a holder can only release its OWN lock —
 * never one that expired and was re-acquired by someone else. This is the primitive
 * the hourly sweep uses to guarantee only one instance polls each due watch.
 */
@Component
public class RedisDistributedLock {

	// Atomic compare-and-delete: only delete the key if it still holds our token.
	private static final String UNLOCK_LUA =
			"if redis.call('get', KEYS[1]) == ARGV[1] then return redis.call('del', KEYS[1]) else return 0 end";

	private final StringRedisTemplate redis;
	private final RedisScript<Long> unlockScript;

	public RedisDistributedLock(StringRedisTemplate redis) {
		this.redis = redis;
		this.unlockScript = new DefaultRedisScript<>(UNLOCK_LUA, Long.class);
	}

	/** Try to acquire {@code key} for {@code token}, auto-expiring after {@code ttl}. */
	public boolean tryLock(String key, String token, Duration ttl) {
		Boolean acquired = redis.opsForValue().setIfAbsent(key, token, ttl);
		return Boolean.TRUE.equals(acquired);
	}

	/** Release {@code key} only if we still hold it under {@code token}. */
	public boolean unlock(String key, String token) {
		Long released = redis.execute(unlockScript, List.of(key), token);
		return released != null && released > 0L;
	}
}
