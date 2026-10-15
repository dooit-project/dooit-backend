package pj.dooit.goal.dto;

import pj.dooit.goal.domain.ActionVariant;
import pj.dooit.goal.domain.NextActionPatchKind;
import pj.dooit.goal.domain.GoalPracticeResult;
import pj.dooit.goal.domain.PracticeDifficulty;

import java.time.LocalDateTime;
import java.util.List;

public record GoalPracticeResultResponse(
        Long occurrenceId,
        ActionVariant actionVariant,
        LocalDateTime practicedAt,
        String practicedTimeZone,
        String accomplishedNote,
        List<PracticeDifficulty> difficulties,
        NextActionPatchKind nextActionPatchKind,
        String nextAction,
        LocalDateTime createdAt,
        LocalDateTime updatedAt,
        long occurrenceVersion
) {
    public static GoalPracticeResultResponse from(GoalPracticeResult result) {
        if (result == null) {
            return null;
        }
        return new GoalPracticeResultResponse(
                result.getOccurrenceId(),
                result.getActionVariant(),
                result.getPracticedAt(),
                result.getPracticedTimeZone(),
                result.getAccomplishedNote(),
                result.difficulties(),
                result.getNextActionPatchKind(),
                result.getNextActionValue(),
                result.getCreatedAt(),
                result.getUpdatedAt(),
                result.getOccurrence().getEntityVersion()
        );
    }
}
