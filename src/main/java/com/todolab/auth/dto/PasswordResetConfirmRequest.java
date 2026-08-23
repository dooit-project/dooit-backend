package com.todolab.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Schema(description = "비밀번호 재설정 확정 요청")
public record PasswordResetConfirmRequest(
        @NotBlank
        @Schema(description = "재설정 token", example = "opaque-reset-token")
        String token,
        @NotBlank
        @Size(min = 8, max = 72)
        @Schema(description = "새 비밀번호", minLength = 8, maxLength = 72)
        String newPassword
) {
}
