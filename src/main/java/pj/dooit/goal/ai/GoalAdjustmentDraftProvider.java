package pj.dooit.goal.ai;

import pj.dooit.goal.dto.GoalPlanActionResponse;

/** Generates text suggestions only. No adapter is installed before external data policies are agreed. */
public interface GoalAdjustmentDraftProvider {
    GoalPlanDraftProvider.Suggestion draft(Context context);

    // No user/resource IDs, dates, schedules or unrequested practice records are transmitted.
    record Context(String goalTitle, String desiredOutcome, String motivationText,
                   String planName, String triggerText, GoalPlanActionResponse standardAction,
                   GoalPlanActionResponse minimumAction, String nextAction, String description) {
        @Override public String toString() { return "GoalAdjustmentDraftContext[REDACTED]"; }
    }
}
