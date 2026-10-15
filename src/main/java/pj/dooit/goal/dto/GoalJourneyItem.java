package pj.dooit.goal.dto;

import pj.dooit.goal.domain.ActionVariant;
import pj.dooit.goal.domain.GoalJourneySourceType;
import pj.dooit.goal.domain.PracticeDifficulty;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

public record GoalJourneyItem(
        GoalJourneySourceType sourceType, Long sourceId, LocalDate practicedDate,
        Long goalId, Integer goalVersion, Long planId, Integer planVersion,
        String title, String actionText, ActionVariant actionVariant, String note, Long taskId,
        LocalDate serviceDate, LocalDateTime practicedAt, String practicedTimeZone,
        String snapshotGoalTitle, String nextAction, List<PracticeDifficulty> difficulties
) {}
