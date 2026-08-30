package pj.dooit.task.dto;

import pj.dooit.Constant;
import pj.dooit.task.domain.TaskType;
import pj.dooit.task.exception.TaskValidationException;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.time.LocalDateTime;
import java.time.LocalTime;

@Schema(description = "Task 생성/수정 요청")
public record TaskRequest(
        @NotBlank(message = "제목은 필수값입니다")
        @Size(max = 30, message = "제목은 30자 이하여야 합니다")
        @Schema(description = "Task 제목", example = "출시 준비", maxLength = 30)
        String title,

        @Size(max = 300, message = "설명은 300자 이하여야 합니다")
        @Schema(description = "Task 설명", example = "체크리스트 정리", maxLength = 300, nullable = true)
        String description,

        @Schema(
                description = "Task 종류. 생략하면 날짜 없는 요청은 TODO, 날짜 있는 요청은 기본 일정 타입으로 처리됩니다.",
                example = "TODO",
                nullable = true
        )
        TaskType type,

        @Schema(description = "시작 일시. offset 없는 LocalDateTime 형식입니다.", example = "2026-07-15T09:00:00", nullable = true)
        LocalDateTime startAt,
        @Schema(description = "종료 일시. startAt 이후여야 하며 endAt만 단독으로 보낼 수 없습니다.", example = "2026-07-15T10:00:00", nullable = true)
        LocalDateTime endAt,

        @Size(max = 30, message = "카테고리는 30자 이하여야 합니다")
        @Schema(description = "카테고리명. '미분류'는 시스템 예약어라 사용할 수 없습니다.", example = "업무", maxLength = 30, nullable = true)
        String category,

        @Schema(description = "예상 소요 시간(분). null이면 사용자가 시간을 정하지 않은 상태입니다.", example = "30", minimum = "5", maximum = "1440", nullable = true)
        Integer estimatedDurationMinutes,

        @Schema(description = "종일 일정 여부. true이면 startAt/endAt은 모두 00:00이어야 합니다.", example = "false")
        boolean allDay,

        @Schema(description = "Task 알림 사용 여부. 생략하면 true입니다.", example = "true", nullable = true)
        Boolean notificationEnabled,

        @Schema(description = "알림 예약 시각. null이면 startAt을 사용합니다.", example = "2026-07-15T08:50:00", nullable = true)
        LocalDateTime notifyAt,

        @Schema(description = "반복 생성 옵션. 없으면 단일 Task로 생성합니다.", nullable = true)
        TaskRecurrenceRequest recurrence
) {

    public TaskRequest(
            String title,
            String description,
            TaskType type,
            LocalDateTime startAt,
            LocalDateTime endAt,
            String category,
            boolean allDay
    ) {
        this(title, description, type, startAt, endAt, category, null, allDay, null, null, null);
    }

    public TaskRequest(
            String title,
            String description,
            LocalDateTime startAt,
            LocalDateTime endAt,
            String category,
            boolean allDay
    ) {
        this(title, description, null, startAt, endAt, category, null, allDay, null, null, null);
    }

    public TaskRequest(
            String title,
            String description,
            TaskType type,
            LocalDateTime startAt,
            LocalDateTime endAt,
            String category,
            boolean allDay,
            TaskRecurrenceRequest recurrence
    ) {
        this(title, description, type, startAt, endAt, category, null, allDay, null, null, recurrence);
    }

    public TaskRequest(
            String title,
            String description,
            TaskType type,
            LocalDateTime startAt,
            LocalDateTime endAt,
            String category,
            boolean allDay,
            Boolean notificationEnabled,
            LocalDateTime notifyAt,
            TaskRecurrenceRequest recurrence
    ) {
        this(title, description, type, startAt, endAt, category, null, allDay, notificationEnabled, notifyAt, recurrence);
    }

    public TaskType normalizedType() {
        if (type != null) {
            return type;
        }

        if (startAt == null && endAt == null) {
            return TaskType.TODO;
        }

        return TaskType.defaultType();
    }

    public void validate() {
        validateEndAtWithoutStartAt();
        validateAllDayTime();
        validateDateTimeOrder();
        validateUnscheduledAllDay();
        validateCategory();
        validateEstimatedDurationMinutes();
        validateNotification();
        validateRecurrence();
    }

    private void validateEndAtWithoutStartAt() {
        if (endAt != null && startAt == null) {
            throw new TaskValidationException("종료 시간만 설정할 수 없습니다. 시작 시간을 함께 설정해주세요.");
        }
    }

    private void validateAllDayTime() {
        if (!allDay) {
            return;
        }

        if (startAt != null && !isMidnight(startAt)) {
            throw new TaskValidationException("종일 일정은 시간을 입력할 수 없습니다. 시작일은 00:00 기준이어야 합니다.");
        }

        if (endAt != null && !isMidnight(endAt)) {
            throw new TaskValidationException("종일 일정은 시간을 입력할 수 없습니다. 종료일은 00:00 기준이어야 합니다.");
        }
    }

    private void validateDateTimeOrder() {
        if (startAt == null || endAt == null) {
            return;
        }

        if (endAt.isBefore(startAt) || endAt.isEqual(startAt)) {
            throw new TaskValidationException("종료 시간은 시작 시간 이후여야 합니다.");
        }
    }

    private void validateUnscheduledAllDay() {
        if (startAt == null && endAt == null && allDay) {
            throw new TaskValidationException("미정 일정에는 종일 설정을 할 수 없습니다.");
        }
    }

    private void validateCategory() {
        if (category == null) {
            return;
        }

        String normalizedCategory = category.trim();
        if (normalizedCategory.isEmpty()) {
            return;
        }

        if (Constant.UNCATEGORIZED.equals(normalizedCategory)) {
            throw new TaskValidationException("'미분류'는 시스템 예약어라서 카테고리명으로 사용할 수 없습니다.");
        }
    }

    private void validateRecurrence() {
        if (recurrence == null) {
            return;
        }
        recurrence.validate(startAt);
    }

    private void validateEstimatedDurationMinutes() {
        if (estimatedDurationMinutes == null) {
            return;
        }
        if (estimatedDurationMinutes < 5 || estimatedDurationMinutes > 1440) {
            throw new TaskValidationException("예상 소요 시간은 5분 이상 1440분 이하여야 합니다.");
        }
    }

    private void validateNotification() {
        boolean enabled = notificationEnabled == null || notificationEnabled;
        if (notifyAt == null) {
            return;
        }
        if (!enabled) {
            throw new TaskValidationException("알림이 비활성화된 Task에는 notifyAt을 설정할 수 없습니다.");
        }
        if (startAt == null) {
            throw new TaskValidationException("notifyAt은 시작 일시가 있는 Task에만 설정할 수 있습니다.");
        }
    }

    private boolean isMidnight(LocalDateTime dt) {
        return dt.toLocalTime().equals(LocalTime.MIDNIGHT);
    }
}
