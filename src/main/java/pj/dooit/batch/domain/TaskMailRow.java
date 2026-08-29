package pj.dooit.batch.domain;

import pj.dooit.task.dto.TaskCategoryGroupResponse;
import pj.dooit.task.dto.TaskResponse;

import java.time.LocalDateTime;
import java.util.List;

public record TaskMailRow(
        Long id,
        String title,
        LocalDateTime startAt,
        LocalDateTime endAt
) {
    public static TaskMailRow from(TaskResponse task) {
        return new TaskMailRow(
                task.id(),
                task.title(),
                task.startAt(),
                task.endAt()
        );
    }

    public static List<TaskMailRow> from(List<TaskResponse> tasks) {
        return tasks.stream()
                .map(TaskMailRow::from)
                .toList();
    }
}
