package pj.dooit.goal.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import pj.dooit.goal.dto.GoalPlanResponse;
import pj.dooit.goal.dto.GoalResponse;
import pj.dooit.goal.exception.GoalPlanNotFoundException;
import pj.dooit.user.domain.User;

@Service
@RequiredArgsConstructor
public class GoalAdjustmentSnapshotService {
    private final GoalService goals;
    private final GoalPlanService plans;

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public Snapshot read(Long goalId, Long planId, User owner) {
        var goal = goals.get(goalId, owner);
        var plan = plans.get(planId, owner);
        if (!goal.id().equals(plan.goalId())) {
            throw new GoalPlanNotFoundException("planId=" + planId);
        }
        return new Snapshot(goal, plan);
    }

    public record Snapshot(GoalResponse goal, GoalPlanResponse plan) {
        @Override public String toString() { return "GoalAdjustmentSnapshot[REDACTED]"; }
    }
}
