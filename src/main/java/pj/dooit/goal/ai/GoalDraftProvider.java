package pj.dooit.goal.ai;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** External adapters require separately agreed transfer/retention/deletion policies and bounded timeouts. */
public interface GoalDraftProvider {
    Suggestion draft(String description);

    record Suggestion(@NotBlank @Size(max = 120) String title,
                      @Size(max = 500) String desiredOutcome,
                      @Size(max = 500) String motivationText) {
        @Override public String toString() { return "Suggestion[REDACTED]"; }
    }
}
