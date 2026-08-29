package pj.dooit.task.dto;

public record TaskRecommendationResponse(
        TaskResponse task,
        String reason
) {
}
