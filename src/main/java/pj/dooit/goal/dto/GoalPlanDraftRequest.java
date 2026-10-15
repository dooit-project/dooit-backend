package pj.dooit.goal.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record GoalPlanDraftRequest(@NotBlank @Size(max = 2000) String description,
                                   @NotNull @Valid GoalPlanScheduleRequest schedule) {
    @Override public String toString() { return "GoalPlanDraftRequest[REDACTED]"; }
}
