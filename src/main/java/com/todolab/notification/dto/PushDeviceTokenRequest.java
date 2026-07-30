package com.todolab.notification.dto;

import com.todolab.notification.domain.PushPlatform;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

@Schema(description = "서버 push 디바이스 토큰 등록 요청")
public record PushDeviceTokenRequest(
        @NotNull(message = "platform은 필수입니다")
        @Schema(description = "push platform", example = "EXPO", allowableValues = {"IOS", "ANDROID", "EXPO"})
        PushPlatform platform,

        @NotBlank(message = "deviceToken은 필수입니다")
        @Size(max = 512, message = "deviceToken은 512자 이하여야 합니다")
        @Schema(description = "APNs/FCM/Expo push token", example = "ExponentPushToken[xxxxxxxxxxxxxxxxxxxxxx]", maxLength = 512)
        String deviceToken,

        @Size(max = 50, message = "appVersion은 50자 이하여야 합니다")
        @Schema(description = "모바일 앱 버전", example = "1.0.0", nullable = true, maxLength = 50)
        String appVersion,

        @Size(max = 100, message = "deviceName은 100자 이하여야 합니다")
        @Schema(description = "사용자 기기명", example = "Hyunseung iPhone", nullable = true, maxLength = 100)
        String deviceName
) {
}
