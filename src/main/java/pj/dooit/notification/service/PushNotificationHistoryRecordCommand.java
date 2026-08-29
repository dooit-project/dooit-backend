package pj.dooit.notification.service;

import pj.dooit.notification.config.PushNotificationProvider;
import pj.dooit.notification.domain.PushNotificationSource;
import pj.dooit.notification.domain.PushNotificationStatus;

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
