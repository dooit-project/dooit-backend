package pj.dooit.goal.dto;

public record GoalAdjustmentDraftResponse(Long goalId, int goalVersion, Long planId, int planVersion,
                                          String name, String triggerText, GoalPlanScheduleResponse schedule,
                                          GoalPlanActionRequest standardAction, GoalPlanActionRequest minimumAction,
                                          String nextAction) {
    @Override public String toString() { return "GoalAdjustmentDraftResponse[REDACTED]"; }
}
