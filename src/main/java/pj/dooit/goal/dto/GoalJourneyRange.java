package pj.dooit.goal.dto;

import java.time.LocalDate;

public record GoalJourneyRange(LocalDate from, LocalDate to, String timeZone) {}
