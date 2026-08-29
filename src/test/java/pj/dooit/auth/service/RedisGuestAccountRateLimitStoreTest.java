package pj.dooit.auth.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.startsWith;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.mock;

class RedisGuestAccountRateLimitStoreTest {

    @Test
    @DisplayName("Redis rate limit store는 increment 결과를 반환하고 최초 생성 key에 TTL을 설정한다")
    void incrementAndGet_setsTtlOnFirstIncrement() {
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        ValueOperations<String, String> valueOperations = mock(ValueOperations.class);
        given(redisTemplate.opsForValue()).willReturn(valueOperations);
        given(valueOperations.increment(startsWith("dooit:guest-rate-limit:client-key:")))
                .willReturn(1L);
        RedisGuestAccountRateLimitStore store = new RedisGuestAccountRateLimitStore(redisTemplate);
        Duration window = Duration.ofHours(1);

        int count = store.incrementAndGet("client-key", window);

        assertThat(count).isEqualTo(1);
        then(valueOperations).should().increment(startsWith("dooit:guest-rate-limit:client-key:"));
        then(redisTemplate).should().expire(startsWith("dooit:guest-rate-limit:client-key:"), any(Duration.class));
    }
}
