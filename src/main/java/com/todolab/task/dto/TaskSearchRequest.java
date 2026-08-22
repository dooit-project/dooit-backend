package com.todolab.task.dto;

import com.todolab.task.domain.TaskStatus;
import com.todolab.task.domain.TaskType;
import com.todolab.task.domain.query.TaskSearchDateField;
import com.todolab.task.domain.query.TaskSearchSort;
import com.todolab.task.exception.TaskValidationException;
import lombok.Getter;

import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Getter
public class TaskSearchRequest {

    public static final int DEFAULT_LIMIT = 50;
    public static final int MAX_LIMIT = 100;

    private final String q;
    private final Set<TaskStatus> statuses;
    private final Set<TaskType> taskTypes;
    private final String category;
    private final Long ddayGoalId;
    private final Boolean hasDday;
    private final Boolean hasRecurrence;
    private final Boolean allDay;
    private final TaskSearchDateField dateField;
    private final LocalDate dateFrom;
    private final LocalDate dateTo;
    private final TaskSearchSort sort;
    private final Long cursorTaskId;
    private final int limit;

    public TaskSearchRequest(
            String q,
            List<String> rawStatuses,
            List<String> rawTaskTypes,
            String category,
            Long ddayGoalId,
            Boolean hasDday,
            Boolean hasRecurrence,
            Boolean allDay,
            String rawDateField,
            LocalDate dateFrom,
            LocalDate dateTo,
            String rawSort,
            String rawCursor,
            Integer limit
    ) {
        this.q = normalizeBlank(q);
        this.statuses = parseEnums(rawStatuses, TaskStatus.class, "statuses");
        this.taskTypes = parseEnums(rawTaskTypes, TaskType.class, "taskTypes");
        this.category = normalizeBlank(category);
        this.ddayGoalId = ddayGoalId;
        this.hasDday = hasDday;
        this.hasRecurrence = hasRecurrence;
        this.allDay = allDay;
        this.dateField = parseEnum(rawDateField, TaskSearchDateField.class, TaskSearchDateField.PLANNED, "dateField");
        this.dateFrom = dateFrom;
        this.dateTo = dateTo;
        this.sort = parseEnum(rawSort, TaskSearchSort.class, TaskSearchSort.RELEVANT_DATE_ASC, "sort");
        this.cursorTaskId = parseCursor(rawCursor);
        this.limit = normalizeLimit(limit);
        validateDateRange(dateFrom, dateTo);
    }

    public boolean hasDateRange() {
        return dateFrom != null || dateTo != null;
    }

    private static String normalizeBlank(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }

    private static <E extends Enum<E>> Set<E> parseEnums(List<String> rawValues, Class<E> enumType, String fieldName) {
        if (rawValues == null || rawValues.isEmpty()) {
            return Set.of();
        }

        try {
            return rawValues.stream()
                    .flatMap(TaskSearchRequest::splitComma)
                    .map(value -> Enum.valueOf(enumType, value.trim().toUpperCase(Locale.ROOT)))
                    .collect(Collectors.toUnmodifiableSet());
        } catch (Exception e) {
            throw new TaskValidationException("올바르지 않은 " + fieldName + " 값입니다.");
        }
    }

    private static Stream<String> splitComma(String rawValue) {
        if (rawValue == null || rawValue.isBlank()) {
            return Stream.empty();
        }
        return Arrays.stream(rawValue.split(","))
                .map(String::trim)
                .filter(value -> !value.isBlank());
    }

    private static <E extends Enum<E>> E parseEnum(String rawValue, Class<E> enumType, E defaultValue, String fieldName) {
        if (rawValue == null || rawValue.isBlank()) {
            return defaultValue;
        }

        try {
            return Enum.valueOf(enumType, rawValue.trim().toUpperCase(Locale.ROOT));
        } catch (Exception e) {
            throw new TaskValidationException("올바르지 않은 " + fieldName + " 값입니다.");
        }
    }

    private static Long parseCursor(String rawCursor) {
        if (rawCursor == null || rawCursor.isBlank()) {
            return null;
        }

        try {
            long cursorTaskId = Long.parseLong(rawCursor.trim());
            if (cursorTaskId <= 0) {
                throw new NumberFormatException();
            }
            return cursorTaskId;
        } catch (NumberFormatException e) {
            throw new TaskValidationException("올바르지 않은 cursor 값입니다.");
        }
    }

    private static int normalizeLimit(Integer limit) {
        if (limit == null) {
            return DEFAULT_LIMIT;
        }
        if (limit < 1 || limit > MAX_LIMIT) {
            throw new TaskValidationException("limit은 1 이상 100 이하이어야 합니다.");
        }
        return limit;
    }

    private static void validateDateRange(LocalDate dateFrom, LocalDate dateTo) {
        if (dateFrom != null && dateTo != null && dateFrom.isAfter(dateTo)) {
            throw new TaskValidationException("dateFrom은 dateTo보다 늦을 수 없습니다.");
        }
    }
}
