package pj.dooit.goal.dto;

import pj.dooit.goal.domain.ActionVariant;
import pj.dooit.goal.domain.NextActionPatchKind;
import pj.dooit.goal.domain.PracticeDifficulty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

import java.time.LocalDateTime;
import java.util.List;

public record GoalPracticeResultRequest(
        @NotNull
        ActionVariant actionVariant,

        @NotNull
        LocalDateTime practicedAt,

        @Size(max = 50)
        String practicedTimeZone,

        @Size(max = 1000)
        String accomplishedNote,

        @Size(max = 5)
        List<@NotNull PracticeDifficulty> difficulties,

        NextActionPatchKind nextActionPatchKind,

        @Size(max = 300)
        String nextAction,

        @PositiveOrZero
        Long occurrenceVersion
) {
}
