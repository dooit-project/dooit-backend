package pj.dooit.goal.dto;

public record GoalPlanActionResponse(
        String text,
        String targetAmount,
        Integer estimatedMinutes
) {
}
