package com.todolab.notification.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.notification.push")
public record PushNotificationProperties(
        boolean enabled,
        PushNotificationProvider provider,
        String endpoint,
        String accessToken
) {

    public PushNotificationProperties {
        if (provider == null) {
            provider = PushNotificationProvider.EXPO;
        }
        if (endpoint == null || endpoint.isBlank()) {
            endpoint = "https://exp.host/--/api/v2/push/send";
        }
    }
}
