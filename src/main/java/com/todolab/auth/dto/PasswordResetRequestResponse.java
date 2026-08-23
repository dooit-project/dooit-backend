package com.todolab.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "비밀번호 재설정 요청 응답")
public record PasswordResetRequestResponse(
        @Schema(description = "요청 접수 여부. 가입 여부와 무관하게 true입니다.", example = "true")
        boolean requested,
        @Schema(description = "재설정 token TTL 초", example = "1800")
        long ttlSeconds
) {
}
