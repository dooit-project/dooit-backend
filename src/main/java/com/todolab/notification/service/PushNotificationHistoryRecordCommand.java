package com.todolab.notification.service;

import com.todolab.notification.config.PushNotificationProvider;
import com.todolab.notification.domain.PushNotificationSource;
import com.todolab.notification.domain.PushNotificationStatus;

import java.time.LocalDate;
import java.time.LocalDateTime;

public record PushNotificationHistoryRecordCommand(
        PushNotificationSource source,
        PushNotificationProvider provider,
        PushNotificationStatus status,
        Long pushDeviceTokenId,
        Long taskId,
        Long recurrenceSeriesId,
        LocalDate occurrenceDate,
        String notificationKey,
        String idempotencyKey,
        String providerMessageId,
        String errorCode,
        String errorMessage,
        LocalDateTime attemptedAt
) {
}
