package com.todolab.notification.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@ConfigurationProperties(prefix = "app.notification.push")
public record PushNotificationProperties(
        boolean enabled,
        PushNotificationProvider provider,
        String endpoint,
        String accessToken,
        Duration schedulerFixedDelay,
        Duration lookAheadWindow
) {

    public PushNotificationProperties {
        if (provider == null) {
            provider = PushNotificationProvider.EXPO;
        }
        if (endpoint == null || endpoint.isBlank()) {
            endpoint = "https://exp.host/--/api/v2/push/send";
        }
        if (accessToken != null && accessToken.isBlank()) {
            accessToken = null;
        }
        if (schedulerFixedDelay == null) {
            schedulerFixedDelay = Duration.ofMinutes(1);
        }
        if (lookAheadWindow == null) {
            lookAheadWindow = Duration.ofMinutes(10);
        }
    }
}
