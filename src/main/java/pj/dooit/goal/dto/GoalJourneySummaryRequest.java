package pj.dooit.goal.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;

public record GoalJourneySummaryRequest(@NotNull LocalDate from, @NotNull LocalDate to,
                                        @Size(max = 50) String timeZone,
                                        @Size(max = 90) @Pattern(regexp = "goal:[1-9][0-9]*:[a-f0-9]{64}") String expectedJourneyVersion) {
    @Override public String toString() { return "GoalJourneySummaryRequest[REDACTED]"; }
}
