package pj.dooit.task.dto;

import pj.dooit.task.domain.RecurrenceFrequency;
import pj.dooit.task.domain.TaskType;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

@Schema(description = "Task 빠른 등록 응답")
public record TaskQuickCaptureResponse(
        @Schema(description = "생성된 Task")
        TaskResponse task,

        @Schema(description = "날짜, 시간 또는 반복 규칙을 파싱해 적용했는지 여부", example = "true")
        boolean parsed,

        @Schema(description = "사용자가 입력한 원문", example = "내일 오후 3시 출시 회의")
        String originalText,

        @Schema(description = "적용된 날짜", type = "string", format = "date", example = "2026-08-14", nullable = true)
        LocalDate parsedDate,

        @Schema(description = "적용된 시간", type = "string", format = "time", example = "15:00:00", nullable = true)
        LocalTime parsedTime,

        @Schema(description = "적용된 Task 종류", example = "SCHEDULE")
        TaskType parsedType,

        @Schema(description = "적용된 반복 주기", example = "WEEKLY", nullable = true)
        RecurrenceFrequency parsedRecurrenceFrequency,

        @Schema(description = "적용된 반복 요일. RFC 5545 BYDAY 코드입니다.", example = "[\"MO\"]")
        List<String> parsedByDays,

        @Schema(description = "파싱에 사용한 timezone", example = "Asia/Seoul")
        String timeZone
) {
}
