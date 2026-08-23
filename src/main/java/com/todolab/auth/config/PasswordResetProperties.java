package com.todolab.auth.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@ConfigurationProperties(prefix = "app.auth.password-reset")
public record PasswordResetProperties(
        Duration tokenTtl,
        Duration rateLimitWindow,
        int maxRequestsPerWindow,
        String linkTemplate
) {

    public PasswordResetProperties {
        tokenTtl = tokenTtl == null ? Duration.ofMinutes(30) : tokenTtl;
        rateLimitWindow = rateLimitWindow == null ? Duration.ofHours(1) : rateLimitWindow;
        maxRequestsPerWindow = maxRequestsPerWindow <= 0 ? 5 : maxRequestsPerWindow;
        if (linkTemplate == null || linkTemplate.isBlank()) {
            linkTemplate = "todolab://password-reset?token={token}";
        }
    }
}
