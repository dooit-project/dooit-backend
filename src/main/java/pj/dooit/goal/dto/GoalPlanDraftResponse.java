package pj.dooit.goal.dto;

public record GoalPlanDraftResponse(Long goalId, int goalVersion, String name, String triggerText,
                                    GoalPlanScheduleRequest schedule, GoalPlanActionRequest standardAction,
                                    GoalPlanActionRequest minimumAction, String nextAction) {
    @Override public String toString() { return "GoalPlanDraftResponse[REDACTED]"; }
}
