package pj.dooit.goal.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record GoalTaskLinkRequest(@NotNull @Positive Long goalId) {}
