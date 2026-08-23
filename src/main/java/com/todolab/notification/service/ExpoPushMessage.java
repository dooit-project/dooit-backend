package com.todolab.notification.service;

import java.util.Map;

public record ExpoPushMessage(
        String to,
        String title,
        String body,
        Map<String, Object> data
) {
}
