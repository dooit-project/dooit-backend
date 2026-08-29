package pj.dooit.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

@Schema(description = "등록 계정 refresh 요청")
public record RefreshRequest(
        @NotBlank
        @Schema(description = "refresh token")
        String refreshToken
) {
}
