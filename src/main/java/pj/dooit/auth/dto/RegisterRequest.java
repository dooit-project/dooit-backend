package pj.dooit.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Schema(description = "회원가입 요청")
public record RegisterRequest(
        @NotBlank
        @Email
        @Size(max = 255)
        @Schema(description = "로그인 이메일", example = "mobile-smoke@example.com", format = "email", maxLength = 255)
        String email,

        @NotBlank
        @Size(min = 8, max = 72)
        @Schema(description = "비밀번호. BCrypt 입력 제한을 고려해 8-72자만 허용합니다.", example = "password123", minLength = 8, maxLength = 72)
        String password,

        @NotBlank
        @Size(max = 50)
        @Schema(description = "사용자 표시 이름", example = "모바일 스모크", maxLength = 50)
        String displayName
) {
}
