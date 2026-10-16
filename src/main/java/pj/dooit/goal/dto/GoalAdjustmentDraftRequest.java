package pj.dooit.goal.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

public record GoalAdjustmentDraftRequest(@NotNull @Positive Long planId,
                                         @NotBlank @Size(max = 2000) String description) {
    @Override public String toString() { return "GoalAdjustmentDraftRequest[REDACTED]"; }
}
