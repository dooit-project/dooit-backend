package pj.dooit.personalsystem.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record PersonalSystemActionRequest(
        @NotBlank
        @Size(max = 500)
        String text,

        @Size(max = 100)
        String targetAmount,

        @Min(1)
        @Max(1440)
        Integer estimatedMinutes
) {
}
