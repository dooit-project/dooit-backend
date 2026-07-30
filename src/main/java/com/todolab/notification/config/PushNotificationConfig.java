package com.todolab.notification.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(PushNotificationProperties.class)
public class PushNotificationConfig {
}
