package pj.dooit.task.dto;

import pj.dooit.Constant;
import pj.dooit.task.domain.Task;
import pj.dooit.task.domain.DeferReason;
import pj.dooit.task.domain.RecurrenceExceptionType;
import pj.dooit.task.domain.TaskStatus;
import pj.dooit.task.domain.TaskType;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;

@Builder
@Schema(description = "Task 응답")
public record TaskResponse(
        @Schema(description = "Task ID", example = "1", requiredMode = Schema.RequiredMode.REQUIRED)
        Long id,
        @Schema(description = "Task 종류", example = "TODO", requiredMode = Schema.RequiredMode.REQUIRED)
        TaskType type,
        @Schema(description = "Task 제목", example = "출시 준비", requiredMode = Schema.RequiredMode.REQUIRED)
        String title,
        @Schema(description = "Task 설명", example = "체크리스트 정리", nullable = true)
        String description,
        @Schema(description = "시작 일시. 날짜 없는 Task는 null입니다.", example = "2026-07-22T09:00:00", nullable = true)
        LocalDateTime startAt,
        @Schema(description = "종료 일시. 단일 일정 또는 날짜 없는 Task는 null입니다.", example = "2026-07-22T10:00:00", nullable = true)
        LocalDateTime endAt,
        @Schema(description = "종일 일정 여부", example = "false", requiredMode = Schema.RequiredMode.REQUIRED)
        boolean allDay,
        @Schema(description = "날짜가 없는 Task 여부", example = "true", requiredMode = Schema.RequiredMode.REQUIRED)
        boolean unscheduled,
        @Schema(description = "카테고리명", example = "업무", nullable = true)
        String category,
        @Schema(description = "Task 알림 사용 여부", example = "true", requiredMode = Schema.RequiredMode.REQUIRED)
        boolean notificationEnabled,
        @Schema(description = "알림 예약 시각. null이면 startAt을 사용합니다.", example = "2026-07-22T08:50:00", nullable = true)
        LocalDateTime notifyAt,
        @Schema(description = "Task 상태", example = "INBOX", requiredMode = Schema.RequiredMode.REQUIRED)
        TaskStatus status,
        @Schema(description = "표시 기준 날짜. targetDate가 있으면 targetDate, 없으면 startAt 날짜입니다.", example = "2026-07-22", nullable = true)
        LocalDate plannedDate,
        @Schema(description = "Today 실행 날짜. Inbox 또는 미정 Task는 null입니다.", example = "2026-07-22", nullable = true)
        LocalDate targetDate,
        @Schema(description = "Today 정렬 순서. Today 정렬 대상이 아니면 null입니다.", example = "0", nullable = true)
        Integer todayOrder,
        @Schema(description = "완료 시각. 완료되지 않은 Task는 null입니다.", example = "2026-07-22T18:00:00", nullable = true)
        LocalDateTime completedAt,
        @Schema(description = "이월 횟수", example = "0", requiredMode = Schema.RequiredMode.REQUIRED)
        int carryOverCount,
        @Schema(description = "이월 횟수가 기준 이상인지 여부", example = "false", requiredMode = Schema.RequiredMode.REQUIRED)
        boolean staleCarryOver,
        @Schema(description = "미룬 이유", example = "TOO_BIG", nullable = true)
        DeferReason deferReason,
        @Schema(description = "미룬 이유 표시명", example = "너무 큼", nullable = true)
        String deferReasonLabel,
        @Schema(description = "연결된 D-Day 목표 ID", example = "1", nullable = true)
        Long ddayGoalId,
        @Schema(description = "연결된 D-Day 목표 제목", example = "출시", nullable = true)
        String ddayGoalTitle,
        @Schema(description = "연결된 D-Day 목표 날짜", example = "2026-08-01", nullable = true)
        LocalDate ddayGoalTargetDate,
        @Schema(description = "연결된 D-Day 목표까지 남은 일수", example = "10", nullable = true)
        Long ddayDaysLeft,
        @Schema(description = "연결된 반복 series ID", example = "1", nullable = true)
        Long recurrenceSeriesId,
        @Schema(description = "이 Task가 나타내는 반복 occurrence 날짜", example = "2026-07-22", nullable = true)
        LocalDate occurrenceDate,
        @Schema(description = "이동/수정 전 원래 반복 occurrence 날짜", example = "2026-07-15", nullable = true)
        LocalDate originalOccurrenceDate,
        @Schema(description = "반복 occurrence 예외 종류", example = "MODIFIED", nullable = true)
        RecurrenceExceptionType recurrenceException,
        @Schema(description = "반복 series 상세. 반복 Task가 아니면 null입니다.", nullable = true)
        TaskRecurrenceResponse recurrence,
        @Schema(description = "생성 시각", example = "2026-07-22T09:30:00", nullable = true)
        LocalDateTime createdAt,
        @Schema(description = "수정 시각. 생성 직후에는 null입니다.", example = "2026-07-22T10:30:00", nullable = true)
        LocalDateTime updatedAt
) {
    public TaskResponse(
            Long id,
            String title,
            String description,
            LocalDateTime startAt,
            LocalDateTime endAt,
            boolean allDay,
            boolean unscheduled,
            String category,
            LocalDateTime createdAt
    ) {
        this(id, TaskType.defaultType(), title, description, startAt, endAt, allDay, unscheduled, category, true, null, null, null, null, null, null, 0, false, null, null, null, null, null, null, null, null, null, null, null, createdAt, null);
    }

    public static TaskResponse from(Task t) {
        var ddayGoal = t.getDdayGoal();
        var recurrenceSeries = t.getRecurrenceSeries();
        return TaskResponse.builder()
                .id(t.getId())
                .type(t.getType())
                .title(t.getTitle())
                .description(t.getDescription())
                .startAt(t.getStartAt())
                .endAt(t.getEndAt())
                .allDay(t.isAllDay())
                .unscheduled(t.isUnscheduled())
                .category(t.getCategory())
                .notificationEnabled(t.isNotificationEnabled())
                .notifyAt(t.getNotifyAt())
                .status(t.getStatus())
                .plannedDate(t.getPlannedDate())
                .targetDate(t.getTargetDate())
                .todayOrder(t.getTodayOrder())
                .completedAt(t.getCompletedAt())
                .carryOverCount(t.getCarryOverCount())
                .staleCarryOver(t.isStaleCarryOver())
                .deferReason(t.getDeferReason())
                .deferReasonLabel(t.getDeferReason() == null ? null : t.getDeferReason().getLabel())
                .ddayGoalId(ddayGoal == null ? null : ddayGoal.getId())
                .ddayGoalTitle(ddayGoal == null ? null : ddayGoal.getTitle())
                .ddayGoalTargetDate(ddayGoal == null ? null : ddayGoal.getTargetDate())
                .ddayDaysLeft(ddayGoal == null ? null : ChronoUnit.DAYS.between(LocalDate.now(Constant.ZONE), ddayGoal.getTargetDate()))
                .recurrenceSeriesId(recurrenceSeries == null ? null : recurrenceSeries.getId())
                .occurrenceDate(t.getOccurrenceDate())
                .originalOccurrenceDate(t.getOriginalOccurrenceDate())
                .recurrenceException(t.getRecurrenceException())
                .recurrence(TaskRecurrenceResponse.from(recurrenceSeries))
                .createdAt(t.getCreatedAt())
                .updatedAt(t.getUpdatedAt())
                .build();
    }
}
