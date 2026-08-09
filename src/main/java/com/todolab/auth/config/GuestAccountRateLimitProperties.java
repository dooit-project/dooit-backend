package com.todolab.auth.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@ConfigurationProperties(prefix = "app.auth.guest.rate-limit")
public record GuestAccountRateLimitProperties(
        boolean enabled,
        Store store,
        int maxRequests,
        Duration window,
        int maxTrackedKeys
) {

    public GuestAccountRateLimitProperties {
        if (store == null) {
            store = Store.MEMORY;
        }
        if (maxRequests <= 0) {
            maxRequests = 30;
        }
        if (window == null || window.isNegative() || window.isZero()) {
            window = Duration.ofHours(1);
        }
        if (maxTrackedKeys <= 0) {
            maxTrackedKeys = 10_000;
        }
    }

    public enum Store {
        MEMORY,
        REDIS
    }
}
