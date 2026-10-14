package pj.dooit.goal.exception;

import lombok.Getter;

@Getter
public class GoalPlanNotFoundException extends RuntimeException {

    private final String detail;

    public GoalPlanNotFoundException(String detail) {
        super(detail);
        this.detail = detail;
    }
}
