package pj.dooit.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Schema(description = "비밀번호 재설정 요청")
public record PasswordResetRequest(
        @NotBlank
        @Email
        @Size(max = 255)
        @Schema(description = "가입 이메일", example = "user@example.com")
        String email
) {
}
