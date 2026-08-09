package com.todolab.auth.service;

import com.todolab.auth.config.GuestAccountRateLimitProperties;
import com.todolab.auth.exception.GuestCreationRateLimitExceededException;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.HexFormat;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
@RequiredArgsConstructor
public class GuestAccountRateLimiter {

    private static final String UNKNOWN_CLIENT_KEY = "unknown";

    private final GuestAccountRateLimitProperties properties;
    private final Map<String, WindowCounter> counters = new ConcurrentHashMap<>();

    public void assertGuestCreationAllowed(HttpServletRequest request) {
        if (!properties.enabled()) {
            return;
        }

        long now = System.currentTimeMillis();
        long windowMillis = properties.window().toMillis();
        String key = hashClientSignal(resolveClientSignal(request));
        counters.compute(key, (ignored, current) -> nextCounter(current, now, windowMillis));
        cleanupExpiredCounters(now, windowMillis);
    }

    private WindowCounter nextCounter(WindowCounter current, long now, long windowMillis) {
        if (current == null || now - current.windowStartedAtMillis() >= windowMillis) {
            return new WindowCounter(now, 1);
        }
        int nextCount = current.count() + 1;
        if (nextCount > properties.maxRequests()) {
            throw new GuestCreationRateLimitExceededException();
        }
        return new WindowCounter(current.windowStartedAtMillis(), nextCount);
    }

    private void cleanupExpiredCounters(long now, long windowMillis) {
        if (counters.size() <= properties.maxTrackedKeys()) {
            return;
        }
        counters.entrySet().removeIf(entry -> now - entry.getValue().windowStartedAtMillis() >= windowMillis);
    }

    private String resolveClientSignal(HttpServletRequest request) {
        if (request == null) {
            return UNKNOWN_CLIENT_KEY;
        }
        String forwardedFor = request.getHeader("X-Forwarded-For");
        if (forwardedFor != null && !forwardedFor.isBlank()) {
            String firstForwardedIp = forwardedFor.split(",", 2)[0].trim();
            if (!firstForwardedIp.isBlank()) {
                return firstForwardedIp;
            }
        }
        String remoteAddr = request.getRemoteAddr();
        return remoteAddr == null || remoteAddr.isBlank() ? UNKNOWN_CLIENT_KEY : remoteAddr;
    }

    private String hashClientSignal(String clientSignal) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hashed = digest.digest(clientSignal.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hashed);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 algorithm is required", e);
        }
    }

    private record WindowCounter(long windowStartedAtMillis, int count) {
    }
}
