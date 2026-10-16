package pj.dooit.goal.dto;

import java.util.List;

public record GoalJourneySummaryResponse(Long goalId, GoalJourneyRange range, String journeyVersion,
                                         GoalJourneySummary summary, String overview, List<GoalJourneyItem> highlights,
                                         int evidenceCount, boolean evidenceTruncated) {
    public GoalJourneySummaryResponse { highlights = List.copyOf(highlights); }
    @Override public String toString() { return "GoalJourneySummaryResponse[REDACTED]"; }
}
