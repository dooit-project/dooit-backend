package pj.dooit.task.dto;

import pj.dooit.task.domain.RecurrenceFrequency;
import pj.dooit.task.domain.TaskType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.time.LocalTime;
import java.util.List;

@Schema(description = "Task 템플릿 생성/수정 요청")
public record TaskTemplateRequest(
        @NotBlank(message = "템플릿 제목은 필수값입니다")
        @Size(max = 30, message = "템플릿 제목은 30자 이하여야 합니다")
        @Schema(description = "템플릿 제목", example = "운동", maxLength = 30)
        String title,

        @Size(max = 300, message = "템플릿 설명은 300자 이하여야 합니다")
        @Schema(description = "템플릿 설명", example = "헬스장", maxLength = 300, nullable = true)
        String description,

        @Schema(description = "생성할 Task 종류. 생략하면 TODO입니다.", example = "SCHEDULE", nullable = true)
        TaskType type,

        @Size(max = 30, message = "카테고리는 30자 이하여야 합니다")
        @Schema(description = "기본 카테고리", example = "건강", maxLength = 30, nullable = true)
        String category,

        @Schema(description = "종일 일정 템플릿 여부", example = "false")
        boolean allDay,

        @Schema(description = "기본 시작 시간. targetDate와 결합해 startAt을 만듭니다.", type = "string", format = "time", example = "09:00:00", nullable = true)
        LocalTime defaultStartTime,

        @Schema(description = "기본 소요 시간(분). 생략하면 60분입니다.", example = "60", nullable = true)
        Integer defaultDurationMinutes,

        @Schema(description = "반복 주기", example = "WEEKLY", nullable = true)
        RecurrenceFrequency recurrenceFrequency,

        @Schema(description = "반복 간격. 생략하면 1입니다.", example = "1", nullable = true)
        Integer recurrenceInterval,

        @Schema(description = "요일 반복 값. WEEKLY에서 사용합니다.", example = "[\"MO\"]", nullable = true)
        List<String> recurrenceByDays
) {
}
