package pj.dooit.goal.exception;

import lombok.Getter;

@Getter
public class GoalDirectLogConflictException extends RuntimeException {

    private final String detail;

    public GoalDirectLogConflictException(String detail) {
        super(detail);
        this.detail = detail;
    }
}
