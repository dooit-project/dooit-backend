package pj.dooit.task.dto;

import pj.dooit.task.domain.RecurrenceFrequency;
import pj.dooit.task.domain.TaskTemplate;
import pj.dooit.task.domain.TaskType;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;

@Schema(description = "Task 템플릿 응답")
public record TaskTemplateResponse(
        Long id,
        String title,
        String description,
        TaskType type,
        String category,
        boolean allDay,
        LocalTime defaultStartTime,
        Integer defaultDurationMinutes,
        RecurrenceFrequency recurrenceFrequency,
        Integer recurrenceInterval,
        List<String> recurrenceByDays,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {

    public static TaskTemplateResponse from(TaskTemplate template) {
        return new TaskTemplateResponse(
                template.getId(),
                template.getTitle(),
                template.getDescription(),
                template.getType(),
                template.getCategory(),
                template.isAllDay(),
                template.getDefaultStartTime(),
                template.getDefaultDurationMinutes(),
                template.getRecurrenceFrequency(),
                template.getRecurrenceInterval(),
                splitByDays(template.getRecurrenceByDays()),
                template.getCreatedAt(),
                template.getUpdatedAt()
        );
    }

    private static List<String> splitByDays(String recurrenceByDays) {
        if (recurrenceByDays == null || recurrenceByDays.isBlank()) {
            return List.of();
        }
        return java.util.Arrays.stream(recurrenceByDays.split(","))
                .map(String::trim)
                .filter(day -> !day.isBlank())
                .toList();
    }
}
