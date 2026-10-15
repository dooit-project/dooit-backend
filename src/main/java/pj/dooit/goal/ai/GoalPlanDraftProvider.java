package pj.dooit.goal.ai;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import pj.dooit.goal.dto.GoalPlanActionRequest;

/** No adapter is installed until provider transfer/retention/deletion policies are agreed. */
public interface GoalPlanDraftProvider {
    Suggestion draft(Context context);

    // Do not pass database IDs, user identifiers, dates, D-Day links or unrelated practice records.
    record Context(String goalTitle, String desiredOutcome, String motivationText, String description) {
        @Override public String toString() { return "GoalPlanDraftContext[REDACTED]"; }
    }

    record Suggestion(@NotBlank @Size(max = 120) String name,
                      @Size(max = 200) String triggerText,
                      @NotNull @Valid GoalPlanActionRequest standardAction,
                      @NotNull @Valid GoalPlanActionRequest minimumAction,
                      @Size(max = 300) String nextAction) {
        @Override public String toString() { return "GoalPlanDraftSuggestion[REDACTED]"; }
    }
}
