package pj.dooit.goal.dto;

import pj.dooit.goal.domain.GoalPlanOccurrence;
import pj.dooit.goal.domain.GoalOccurrenceStatus;
import java.time.LocalDate;
import java.time.LocalDateTime;

public record GoalPlanOccurrenceResponse(
        Long id, Long goalId, Integer goalVersion, Long planId, int planVersion,
        LocalDate serviceDate, String timeZone, GoalOccurrenceStatus status, Long linkedTaskId,
        String notificationKey, LocalDateTime scheduledNotificationAt,
        String snapshotGoalTitle, String snapshotPlanName, String snapshotTriggerText,
        String snapshotStandardActionText, String snapshotMinimumActionText, String snapshotNextAction,
        long occurrenceVersion
) {
    public static GoalPlanOccurrenceResponse from(GoalPlanOccurrence o, boolean notificationEnabled) {
        boolean notify = notificationEnabled && o.getStatus() == GoalOccurrenceStatus.PENDING
                && o.getSnapshotNotifyAtTime() != null;
        return new GoalPlanOccurrenceResponse(o.getId(), o.getGoalId(), o.getGoalVersion(),
                o.getPlan().getId(), o.getPlanVersion(), o.getServiceDate(), o.getTimeZone(),
                o.getStatus(), o.getLinkedTaskId(),
                "goal-plan:%d:%s".formatted(o.getPlan().getId(), o.getServiceDate()),
                notify ? o.getServiceDate().atTime(o.getSnapshotNotifyAtTime()) : null,
                o.getSnapshotGoalTitle(), o.getSnapshotPlanName(), o.getSnapshotTriggerText(),
                o.getSnapshotStandardActionText(), o.getSnapshotMinimumActionText(), o.getSnapshotNextAction(),
                o.getEntityVersion());
    }
}
