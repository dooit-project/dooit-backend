package pj.dooit.goal.dto;

import pj.dooit.goal.domain.GoalPlan;
import pj.dooit.goal.domain.GoalPlanStatus;
import pj.dooit.goal.domain.GoalPlanVersion;

import java.time.LocalDateTime;

public record GoalPlanResponse(
        Long id,
        Long goalId,
        GoalPlanStatus status,
        int currentVersion,
        String name,
        String triggerText,
        GoalPlanScheduleResponse schedule,
        GoalPlanActionResponse standardAction,
        GoalPlanActionResponse minimumAction,
        String nextAction,
        LocalDateTime archivedAt,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
    public static GoalPlanResponse from(GoalPlan plan, GoalPlanVersion version) {
        return new GoalPlanResponse(
                plan.getId(),
                plan.getGoal() == null ? null : plan.getGoal().getId(),
                plan.getStatus(),
                plan.getCurrentVersion(),
                version.getName(),
                version.getTriggerText(),
                GoalPlanScheduleResponse.from(version),
                new GoalPlanActionResponse(
                        version.getStandardActionText(),
                        version.getStandardTargetAmount(),
                        version.getStandardEstimatedMinutes()
                ),
                new GoalPlanActionResponse(
                        version.getMinimumActionText(),
                        version.getMinimumTargetAmount(),
                        version.getMinimumEstimatedMinutes()
                ),
                version.getNextAction(),
                plan.getArchivedAt(),
                plan.getCreatedAt(),
                plan.getUpdatedAt()
        );
    }
}
