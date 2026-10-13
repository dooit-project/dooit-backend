package pj.dooit.goal.dto;

import pj.dooit.goal.domain.Goal;
import pj.dooit.goal.domain.GoalStatus;

import java.time.LocalDate;
import java.time.LocalDateTime;

public record GoalResponse(
        Long id,
        String title,
        String desiredOutcome,
        String motivationText,
        LocalDate targetDate,
        GoalStatus status,
        int currentVersion,
        Long linkedDdayGoalId,
        LocalDateTime achievedAt,
        LocalDateTime archivedAt,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
    public static GoalResponse from(Goal goal) {
        return new GoalResponse(
                goal.getId(),
                goal.getTitle(),
                goal.getDesiredOutcome(),
                goal.getMotivationText(),
                goal.getTargetDate(),
                goal.getStatus(),
                goal.getCurrentVersion(),
                goal.getLinkedDdayGoalId(),
                goal.getAchievedAt(),
                goal.getArchivedAt(),
                goal.getCreatedAt(),
                goal.getUpdatedAt()
        );
    }
}
