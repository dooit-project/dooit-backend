package com.todolab.common.api;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.todolab.Constant;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;

@Schema(description = "공통 API 응답 envelope")
public record ApiResponse<T>(
        @Schema(description = "응답 상태", example = "success", allowableValues = {"success", "fail"})
        String status,
        @JsonInclude(JsonInclude.Include.ALWAYS)
        @Schema(description = "성공 응답 데이터. 삭제 성공 또는 실패 응답에서는 null입니다.", nullable = true)
        T data,
        @JsonInclude(JsonInclude.Include.NON_NULL)
        @Schema(description = "실패 응답 상세. 성공 응답에서는 null입니다.", nullable = true)
        ErrorBody error,
        @Schema(description = "서버 응답 시각", example = "2026-07-14T09:30:00")
        LocalDateTime timestamp
) {

    public static <T> ApiResponse<T> success(T data) {
        return new ApiResponse<>("success", data, null, LocalDateTime.now(Constant.ZONE));
    }

    public static <T> ApiResponse<T> failure(ErrorCode errorCode) {
        return new ApiResponse<>("fail", null, new ErrorBody(errorCode.getCode(), errorCode.getMessage()), LocalDateTime.now(Constant.ZONE));
    }

    @Schema(description = "공통 API 오류 응답 본문")
    public record ErrorBody(
            @Schema(description = "서비스 오류 코드", example = "11002")
            int code,
            @Schema(description = "클라이언트에 노출 가능한 오류 메시지", example = "인증이 필요합니다.")
            String message
    ) {}
}
