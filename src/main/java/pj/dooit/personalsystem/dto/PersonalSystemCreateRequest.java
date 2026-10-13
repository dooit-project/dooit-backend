package pj.dooit.personalsystem.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record PersonalSystemCreateRequest(
        @NotBlank
        @Size(max = 500)
        String directionText,

        @Size(max = 500)
        String motivationText,

        @NotBlank
        @Size(max = 200)
        String triggerText,

        @NotNull
        @Valid
        PersonalSystemScheduleRequest schedule,

        @NotNull
        @Valid
        PersonalSystemActionRequest standardAction,

        @NotNull
        @Valid
        PersonalSystemActionRequest minimumAction,

        @Size(max = 300)
        String nextAction,

        Long linkedDdayGoalId
) {
}
