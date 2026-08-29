package pj.dooit.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "비밀번호 재설정 token 검증 응답")
public record PasswordResetVerifyResponse(
        @Schema(description = "token 사용 가능 여부", example = "true")
        boolean valid,
        @Schema(description = "재설정 대상 email masking 값", example = "u***@example.com")
        String maskedEmail
) {
}
