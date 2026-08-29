package pj.dooit.auth.service;

import java.time.Duration;

public interface GuestAccountRateLimitStore {

    int incrementAndGet(String key, Duration window);
}
