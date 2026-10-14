package pj.dooit.goal.dto;

import pj.dooit.goal.domain.GoalPlanStatus;
import jakarta.validation.constraints.NotNull;

public record GoalPlanStatusRequest(
        @NotNull
        GoalPlanStatus status
) {
}
