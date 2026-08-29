package pj.dooit.task.dto;

import pj.dooit.task.domain.TaskType;
import pj.dooit.task.domain.query.TaskQueryType;
import pj.dooit.task.exception.TaskValidationException;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDate;
import java.time.YearMonth;

@Getter
public class TaskQueryRequest {

    private final TaskQueryType type;
    private final TaskType taskType;
    private final String date;

    @Builder
    public TaskQueryRequest(String rawType, String rawTaskType, String rawDate) {
        this(parseType(rawType), parseTaskType(rawTaskType), rawDate);
    }

    public TaskQueryRequest(TaskQueryType type, String rawDate) {
        this(type, TaskType.defaultType(), rawDate);
    }

    public TaskQueryRequest(TaskQueryType type, TaskType taskType, String rawDate) {
        validateDateFormat(type, rawDate);
        this.type = type;
        this.taskType = taskType == null ? TaskType.defaultType() : taskType;
        this.date = rawDate;
    }

    private static TaskQueryType parseType(String rawType) {
        try {
            return TaskQueryType.from(rawType);
        } catch (Exception e) {
            throw new TaskValidationException("올바르지 않은 Type 값입니다.");
        }
    }

    private static TaskType parseTaskType(String rawTaskType) {
        if (rawTaskType == null || rawTaskType.isBlank()) {
            return TaskType.defaultType();
        }

        try {
            return TaskType.valueOf(rawTaskType.trim().toUpperCase());
        } catch (Exception e) {
            throw new TaskValidationException("올바르지 않은 taskType 값입니다.");
        }
    }

    private static void validateDateFormat(TaskQueryType type, String rawDate) {
        try {
            if (TaskQueryType.MONTH.equals(type)) {
                YearMonth.parse(rawDate);
            } else {
                LocalDate.parse(rawDate);
            }
        } catch (Exception e) {
            throw new TaskValidationException("올바르지 않은 date 값입니다.");
        }
    }
}
