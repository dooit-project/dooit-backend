package pj.dooit.personalsystem.dto;

import pj.dooit.personalsystem.domain.ActionVariant;
import pj.dooit.personalsystem.domain.NextActionPatchKind;
import pj.dooit.personalsystem.domain.PracticeDifficulty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDateTime;
import java.util.List;

public record PersonalSystemPracticeResultRequest(
        @NotNull
        ActionVariant actionVariant,

        @NotNull
        LocalDateTime practicedAt,

        @Size(max = 50)
        String practicedTimeZone,

        @Size(max = 1000)
        String accomplishedNote,

        @Size(max = 5)
        List<PracticeDifficulty> difficulties,

        NextActionPatchKind nextActionPatchKind,

        @Size(max = 300)
        String nextAction,

        Long occurrenceVersion
) {
}
