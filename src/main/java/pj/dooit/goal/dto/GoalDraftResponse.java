package pj.dooit.goal.dto;

import java.time.LocalDate;

/** An unsaved suggestion. Only the user's subsequent goal creation request persists it. */
public record GoalDraftResponse(String title, String desiredOutcome, String motivationText, LocalDate targetDate) {
    @Override public String toString() { return "GoalDraftResponse[REDACTED]"; }
}
