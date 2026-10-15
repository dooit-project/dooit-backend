package pj.dooit.goal.dto;

import pj.dooit.goal.domain.GoalDirectLog;
import java.time.LocalDate;
import java.time.LocalDateTime;

public record GoalDirectLogResponse(
        Long id, Long goalId, int goalVersion, LocalDate practicedDate, String practicedTimeZone,
        String title, String note, LocalDateTime createdAt, LocalDateTime updatedAt, LocalDateTime canceledAt
) {
    public static GoalDirectLogResponse from(GoalDirectLog log) {
        return new GoalDirectLogResponse(log.getId(), log.getGoal().getId(), log.getGoalVersion(),
                log.getPracticedDate(), log.getPracticedTimeZone(), log.getTitle(), log.getNote(),
                log.getCreatedAt(), log.getUpdatedAt(), log.getCanceledAt());
    }
}
