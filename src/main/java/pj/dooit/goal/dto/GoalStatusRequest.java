package pj.dooit.goal.dto;

import pj.dooit.goal.domain.GoalFuturePlanPolicy;
import pj.dooit.goal.domain.GoalStatus;
import jakarta.validation.constraints.NotNull;

public record GoalStatusRequest(
        @NotNull
        GoalStatus status,

        GoalFuturePlanPolicy futurePlanPolicy
) {
}
