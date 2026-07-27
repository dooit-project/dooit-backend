package com.todolab.task.dto;

import com.todolab.task.domain.Task;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Schema(description = "모바일 로컬 알림 예약 후보")
public record TaskNotificationCandidateResponse(
        @Schema(description = "알림 예약 key", example = "task:1")
        String notificationKey,
        @Schema(description = "알림 기준 Task ID", example = "1")
        Long taskId,
        @Schema(description = "알림 예약 시각", example = "2026-07-28T09:00:00")
        LocalDateTime scheduledAt,
        @Schema(description = "반복 series ID. 반복 Task가 아니면 null입니다.", example = "10", nullable = true)
        Long recurrenceSeriesId,
        @Schema(description = "반복 occurrence 날짜. 반복 Task가 아니면 null입니다.", example = "2026-07-28", nullable = true)
        LocalDate occurrenceDate,
        @Schema(description = "Task 응답 원본")
        TaskResponse task
) {

    public static TaskNotificationCandidateResponse from(Task task) {
        Long recurrenceSeriesId = task.getRecurrenceSeries() == null ? null : task.getRecurrenceSeries().getId();
        LocalDate occurrenceDate = task.getOccurrenceDate();
        return new TaskNotificationCandidateResponse(
                notificationKey(task, recurrenceSeriesId, occurrenceDate),
                task.getId(),
                task.getStartAt(),
                recurrenceSeriesId,
                occurrenceDate,
                TaskResponse.from(task)
        );
    }

    private static String notificationKey(Task task, Long recurrenceSeriesId, LocalDate occurrenceDate) {
        if (recurrenceSeriesId != null && occurrenceDate != null) {
            return "recurrence:%d:%s".formatted(recurrenceSeriesId, occurrenceDate);
        }
        return "task:%d".formatted(task.getId());
    }
}
