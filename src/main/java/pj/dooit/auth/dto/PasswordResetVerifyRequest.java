package pj.dooit.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

@Schema(description = "비밀번호 재설정 token 검증 요청")
public record PasswordResetVerifyRequest(
        @NotBlank
        @Schema(description = "재설정 token", example = "opaque-reset-token")
        String token
) {
}
