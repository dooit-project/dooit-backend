package pj.dooit.goal.exception;

import lombok.Getter;

@Getter
public class GoalValidationException extends RuntimeException {

    private final String detail;

    public GoalValidationException(String detail) {
        super(detail);
        this.detail = detail;
    }
}
