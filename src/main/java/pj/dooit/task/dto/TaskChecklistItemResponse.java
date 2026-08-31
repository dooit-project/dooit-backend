package pj.dooit.task.dto;

import pj.dooit.task.domain.TaskChecklistItem;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;

@Schema(description = "Task checklist item 응답")
public record TaskChecklistItemResponse(
        Long id,
        Long taskId,
        String title,
        boolean done,
        int sortOrder,
        LocalDateTime completedAt,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
    public static TaskChecklistItemResponse from(TaskChecklistItem item) {
        return new TaskChecklistItemResponse(
                item.getId(),
                item.getTask().getId(),
                item.getTitle(),
                item.isDone(),
                item.getSortOrder(),
                item.getCompletedAt(),
                item.getCreatedAt(),
                item.getUpdatedAt()
        );
    }
}
