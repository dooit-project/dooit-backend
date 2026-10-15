package pj.dooit.goal.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;

public record GoalDraftRequest(@NotBlank @Size(max = 2000) String description, LocalDate targetDate) {
    @Override public String toString() { return "GoalDraftRequest[REDACTED]"; }
}
