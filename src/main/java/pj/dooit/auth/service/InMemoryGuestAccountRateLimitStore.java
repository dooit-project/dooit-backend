package pj.dooit.auth.service;

import pj.dooit.auth.config.GuestAccountRateLimitProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
@RequiredArgsConstructor
@ConditionalOnProperty(
        name = "app.auth.guest.rate-limit.store",
        havingValue = "memory",
        matchIfMissing = true
)
public class InMemoryGuestAccountRateLimitStore implements GuestAccountRateLimitStore {

    private final GuestAccountRateLimitProperties properties;
    private final Map<String, WindowCounter> counters = new ConcurrentHashMap<>();

    @Override
    public int incrementAndGet(String key, Duration window) {
        long now = System.currentTimeMillis();
        long windowMillis = window.toMillis();
        WindowCounter counter = counters.compute(key, (ignored, current) -> nextCounter(current, now, windowMillis));
        cleanupExpiredCounters(now, windowMillis);
        return counter.count();
    }

    private WindowCounter nextCounter(WindowCounter current, long now, long windowMillis) {
        if (current == null || now - current.windowStartedAtMillis() >= windowMillis) {
            return new WindowCounter(now, 1);
        }
        return new WindowCounter(current.windowStartedAtMillis(), current.count() + 1);
    }

    private void cleanupExpiredCounters(long now, long windowMillis) {
        if (counters.size() <= properties.maxTrackedKeys()) {
            return;
        }
        counters.entrySet().removeIf(entry -> now - entry.getValue().windowStartedAtMillis() >= windowMillis);
    }

    private record WindowCounter(long windowStartedAtMillis, int count) {
    }
}
