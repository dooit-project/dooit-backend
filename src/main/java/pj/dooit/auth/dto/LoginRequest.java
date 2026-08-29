package pj.dooit.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Schema(description = "로그인 요청")
public record LoginRequest(
        @NotBlank
        @Email
        @Size(max = 255)
        @Schema(description = "로그인 이메일", example = "mobile-smoke@example.com", format = "email", maxLength = 255)
        String email,

        @NotBlank
        @Size(max = 72)
        @Schema(description = "비밀번호", example = "password123", maxLength = 72)
        String password
) {
}
