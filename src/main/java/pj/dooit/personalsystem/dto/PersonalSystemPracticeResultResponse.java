package pj.dooit.personalsystem.dto;

import pj.dooit.personalsystem.domain.ActionVariant;
import pj.dooit.personalsystem.domain.NextActionPatchKind;
import pj.dooit.personalsystem.domain.PersonalSystemPracticeResult;
import pj.dooit.personalsystem.domain.PracticeDifficulty;

import java.time.LocalDateTime;
import java.util.List;

public record PersonalSystemPracticeResultResponse(
        Long occurrenceId,
        ActionVariant actionVariant,
        LocalDateTime practicedAt,
        String practicedTimeZone,
        String accomplishedNote,
        List<PracticeDifficulty> difficulties,
        NextActionPatchKind nextActionPatchKind,
        String nextAction,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
    public static PersonalSystemPracticeResultResponse from(PersonalSystemPracticeResult result) {
        if (result == null) {
            return null;
        }
        return new PersonalSystemPracticeResultResponse(
                result.getOccurrenceId(),
                result.getActionVariant(),
                result.getPracticedAt(),
                result.getPracticedTimeZone(),
                result.getAccomplishedNote(),
                result.difficulties(),
                result.getNextActionPatchKind(),
                result.getNextActionValue(),
                result.getCreatedAt(),
                result.getUpdatedAt()
        );
    }
}
