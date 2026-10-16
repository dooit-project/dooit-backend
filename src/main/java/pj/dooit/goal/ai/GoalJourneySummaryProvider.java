package pj.dooit.goal.ai;

import pj.dooit.goal.domain.ActionVariant;
import pj.dooit.goal.domain.GoalJourneySourceType;
import pj.dooit.goal.domain.PracticeDifficulty;
import pj.dooit.goal.dto.GoalJourneyRange;
import pj.dooit.goal.dto.GoalJourneySummary;
import java.time.LocalDate;
import java.util.List;

/** Extractive summaries: providers select evidence indices, never generate metrics or factual prose. */
public interface GoalJourneySummaryProvider {
    Selection select(Context context);

    record Context(GoalJourneyRange range, GoalJourneySummary summary, List<Evidence> evidence, boolean evidenceTruncated) {
        public Context { evidence = List.copyOf(evidence); }
        @Override public String toString() { return "GoalJourneySummaryContext[REDACTED]"; }
    }

    // Indices are request-local, zero-based. Database/user IDs and cursor/version tokens are not transmitted.
    record Evidence(int index, GoalJourneySourceType sourceType, LocalDate practicedDate, String title,
                    String actionText, ActionVariant actionVariant, String note, String nextAction,
                    List<PracticeDifficulty> difficulties) {
        public Evidence { difficulties = List.copyOf(difficulties); }
        @Override public String toString() { return "GoalJourneySummaryEvidence[REDACTED]"; }
    }

    record Selection(List<Integer> evidenceIndices) {}
}
