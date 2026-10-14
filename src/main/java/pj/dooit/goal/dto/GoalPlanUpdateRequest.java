package pj.dooit.goal.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

public record GoalPlanUpdateRequest(
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
        String nextAction,

        LocalDate effectiveFromDate
) {
}
