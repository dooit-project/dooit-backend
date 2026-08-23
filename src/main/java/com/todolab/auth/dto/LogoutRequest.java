package com.todolab.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "로그아웃 요청")
public record LogoutRequest(
        @Schema(description = "폐기할 refresh token. 없으면 access token 사용자 기준 활성 세션을 폐기합니다.", nullable = true)
        String refreshToken
) {
}
