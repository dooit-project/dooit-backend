package com.todolab.auth.service;

import com.todolab.auth.config.GuestAccountRateLimitProperties;
import com.todolab.auth.exception.GuestCreationRateLimitExceededException;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

@Service
@RequiredArgsConstructor
public class GuestAccountRateLimiter {

    private static final String UNKNOWN_CLIENT_KEY = "unknown";

    private final GuestAccountRateLimitProperties properties;
    private final GuestAccountRateLimitStore store;

    public void assertGuestCreationAllowed(HttpServletRequest request) {
        if (!properties.enabled()) {
            return;
        }

        String key = hashClientSignal(resolveClientSignal(request));
        int count = store.incrementAndGet(key, properties.window());
        if (count > properties.maxRequests()) {
            throw new GuestCreationRateLimitExceededException();
        }
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
}
