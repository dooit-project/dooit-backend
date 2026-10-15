package pj.dooit.goal.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;

public record GoalTaskCreateRequest(
        @NotBlank @Size(max = 30) String title,
        @NotNull LocalDate date
) {}
