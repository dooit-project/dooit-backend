package pj.dooit.notification.dto;

import pj.dooit.notification.config.PushNotificationProvider;
import pj.dooit.notification.domain.PushNotificationHistory;
import pj.dooit.notification.domain.PushNotificationSource;
import pj.dooit.notification.domain.PushNotificationStatus;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Schema(description = "서버 push 알림 전송 이력 응답")
public record PushNotificationHistoryResponse(
        @Schema(description = "전송 이력 ID", example = "1")
        Long id,
        @Schema(description = "알림 source", example = "SERVER")
        PushNotificationSource source,
        @Schema(description = "push provider", example = "EXPO")
        PushNotificationProvider provider,
        @Schema(description = "전송 상태", example = "SUCCESS")
        PushNotificationStatus status,
        @Schema(description = "push token row ID", example = "10", nullable = true)
        Long pushDeviceTokenId,
        @Schema(description = "device token 마지막 6자", example = "abcd12", nullable = true)
        String tokenSuffix,
        @Schema(description = "알림 기준 Task ID", example = "42", nullable = true)
        Long taskId,
        @Schema(description = "반복 series ID", example = "7", nullable = true)
        Long recurrenceSeriesId,
        @Schema(description = "반복 occurrence 날짜", example = "2026-07-30", nullable = true)
        LocalDate occurrenceDate,
        @Schema(description = "알림 예약 key", example = "task:42")
        String notificationKey,
        @Schema(description = "중복 발송 방지 key", example = "SERVER:42")
        String idempotencyKey,
        @Schema(description = "provider message ID", example = "expo-ticket-id", nullable = true)
        String providerMessageId,
        @Schema(description = "provider 오류 코드", example = "DeviceNotRegistered", nullable = true)
        String errorCode,
        @Schema(description = "마스킹 또는 축약된 오류 메시지", example = "Device not registered", nullable = true)
        String errorMessage,
        @Schema(description = "전송 시도 시각", example = "2026-07-30T09:00:00")
        LocalDateTime attemptedAt,
        @Schema(description = "이력 생성 시각", example = "2026-07-30T09:00:01")
        LocalDateTime createdAt
) {

    public static PushNotificationHistoryResponse from(PushNotificationHistory history) {
        return new PushNotificationHistoryResponse(
                history.getId(),
                history.getSource(),
                history.getProvider(),
                history.getStatus(),
                history.getPushDeviceToken() == null ? null : history.getPushDeviceToken().getId(),
                history.getPushDeviceToken() == null ? null : suffix(history.getPushDeviceToken().getDeviceToken()),
                history.getTaskId(),
                history.getRecurrenceSeriesId(),
                history.getOccurrenceDate(),
                history.getNotificationKey(),
                history.getIdempotencyKey(),
                history.getProviderMessageId(),
                history.getErrorCode(),
                history.getErrorMessage(),
                history.getAttemptedAt(),
                history.getCreatedAt()
        );
    }

    private static String suffix(String token) {
        if (token == null) {
            return null;
        }
        int start = Math.max(0, token.length() - 6);
        return token.substring(start);
    }
}
