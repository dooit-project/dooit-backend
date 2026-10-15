package pj.dooit.goal.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;

public record GoalDirectLogRequest(
        @NotNull LocalDate practicedDate,
        @Size(max = 50) String practicedTimeZone,
        @NotBlank @Size(max = 120) String title,
        @Size(max = 1000) String note
) {}
