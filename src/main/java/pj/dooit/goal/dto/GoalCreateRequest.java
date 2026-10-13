package pj.dooit.goal.dto;

import jakarta.validation.constraints.Size;
import jakarta.validation.constraints.NotBlank;

import java.time.LocalDate;

public record GoalCreateRequest(
        @NotBlank
        @Size(max = 120)
        String title,

        @Size(max = 500)
        String desiredOutcome,

        @Size(max = 500)
        String motivationText,

        LocalDate targetDate,

        Long linkedDdayGoalId
) {
}
