package com.todolab.notification.dto;

import com.todolab.notification.domain.PushDeviceToken;
import com.todolab.notification.domain.PushPlatform;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;

@Schema(description = "서버 push 디바이스 토큰 응답")
public record PushDeviceTokenResponse(
        @Schema(description = "push token row ID", example = "1")
        Long id,
        @Schema(description = "push platform", example = "EXPO")
        PushPlatform platform,
        @Schema(description = "device token 마지막 6자", example = "abcd12")
        String tokenSuffix,
        @Schema(description = "모바일 앱 버전", example = "1.0.0", nullable = true)
        String appVersion,
        @Schema(description = "사용자 기기명", example = "Hyunseung iPhone", nullable = true)
        String deviceName,
        @Schema(description = "활성 여부", example = "true")
        boolean active,
        @Schema(description = "마지막 등록 시각", example = "2026-07-29T09:30:00")
        LocalDateTime lastRegisteredAt,
        @Schema(description = "생성 시각", example = "2026-07-29T09:30:00")
        LocalDateTime createdAt,
        @Schema(description = "수정 시각", example = "2026-07-29T09:30:00", nullable = true)
        LocalDateTime updatedAt
) {

    public static PushDeviceTokenResponse from(PushDeviceToken token) {
        return new PushDeviceTokenResponse(
                token.getId(),
                token.getPlatform(),
                suffix(token.getDeviceToken()),
                token.getAppVersion(),
                token.getDeviceName(),
                token.isActive(),
                token.getLastRegisteredAt(),
                token.getCreatedAt(),
                token.getUpdatedAt()
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
