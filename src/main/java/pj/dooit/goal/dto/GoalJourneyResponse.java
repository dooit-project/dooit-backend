package pj.dooit.goal.dto;

import java.util.List;

public record GoalJourneyResponse(
        Long goalId, GoalJourneyRange range, GoalJourneySummary summary,
        List<GoalJourneyItem> items, String nextCursor, String journeyVersion
) {}
