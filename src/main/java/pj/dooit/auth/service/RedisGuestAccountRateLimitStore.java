package pj.dooit.auth.service;

import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;

@Component
@RequiredArgsConstructor
@ConditionalOnProperty(
        name = "app.auth.guest.rate-limit.store",
        havingValue = "redis"
)
public class RedisGuestAccountRateLimitStore implements GuestAccountRateLimitStore {

    private static final String KEY_PREFIX = "dooit:guest-rate-limit:";

    private final StringRedisTemplate redisTemplate;

    @Override
    public int incrementAndGet(String key, Duration window) {
        String redisKey = KEY_PREFIX + key + ":" + windowBucket(window);
        Long count = redisTemplate.opsForValue().increment(redisKey);
        if (count != null && count == 1L) {
            redisTemplate.expire(redisKey, window);
        }
        return count == null ? 1 : count.intValue();
    }

    private long windowBucket(Duration window) {
        return System.currentTimeMillis() / window.toMillis();
    }
}
