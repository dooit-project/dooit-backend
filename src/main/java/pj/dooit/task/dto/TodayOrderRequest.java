package pj.dooit.task.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;
import java.util.List;

@Schema(description = "Today Task 일괄 재정렬 요청")
public record TodayOrderRequest(
        @NotNull(message = "date는 필수값입니다")
        @Schema(description = "재정렬 대상 Today 날짜", example = "2026-07-15", requiredMode = Schema.RequiredMode.REQUIRED)
        LocalDate date,

        @NotEmpty(message = "orderedTaskIds는 1개 이상이어야 합니다")
        @Schema(description = "저장할 Today Task ID 전체 순서", example = "[3, 1, 2]", requiredMode = Schema.RequiredMode.REQUIRED)
        List<@NotNull(message = "orderedTaskIds에는 null을 포함할 수 없습니다") Long> orderedTaskIds
) {
}
