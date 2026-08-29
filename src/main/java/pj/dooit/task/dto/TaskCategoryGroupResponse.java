package pj.dooit.task.dto;

import java.util.List;

public record TaskCategoryGroupResponse(
        String category,
        List<TaskResponse> tasks
) {
}
