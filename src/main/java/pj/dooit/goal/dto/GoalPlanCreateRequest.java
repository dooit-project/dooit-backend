package pj.dooit.goal.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record GoalPlanCreateRequest(
        Long goalId,

        @NotBlank
        @Size(max = 120)
        String name,

        @Size(max = 200)
        String triggerText,

        @NotNull
        @Valid
        GoalPlanScheduleRequest schedule,

        @NotNull
        @Valid
        GoalPlanActionRequest standardAction,

        @NotNull
        @Valid
        GoalPlanActionRequest minimumAction,

        @Size(max = 300)
        String nextAction
) {
    public GoalPlanCreateRequest withGoalId(Long goalId) {
        return new GoalPlanCreateRequest(
                goalId,
                name,
                triggerText,
                schedule,
                standardAction,
                minimumAction,
                nextAction
        );
    }
}
