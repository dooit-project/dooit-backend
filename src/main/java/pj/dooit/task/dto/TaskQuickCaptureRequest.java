package pj.dooit.task.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

@Schema(description = "Task 빠른 등록 요청")
public record TaskQuickCaptureRequest(
        @NotBlank(message = "빠른 등록 원문은 필수값입니다")
        @Size(max = 100, message = "빠른 등록 원문은 100자 이하여야 합니다")
        @Schema(description = "사용자가 입력한 원문", example = "내일 오후 3시 출시 회의", maxLength = 100)
        String text,

        @Schema(description = "파싱 기준 날짜. 생략하면 사용자 timezone의 오늘입니다.", type = "string", format = "date", example = "2026-08-13", nullable = true)
        LocalDate referenceDate,

        @Schema(description = "파싱 기준 IANA timezone. 생략하면 사용자 timezone입니다.", example = "Asia/Seoul", nullable = true)
        String timeZone,

        @Size(max = 30, message = "기본 카테고리는 30자 이하여야 합니다")
        @Schema(description = "파싱 결과에 적용할 기본 카테고리", example = "업무", maxLength = 30, nullable = true)
        String defaultCategory
) {
}
