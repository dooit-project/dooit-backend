package pj.dooit.personalsystem.dto;

import pj.dooit.personalsystem.domain.PersonalSystemStatus;
import jakarta.validation.constraints.NotNull;

public record PersonalSystemStatusRequest(
        @NotNull
        PersonalSystemStatus status
) {
}
