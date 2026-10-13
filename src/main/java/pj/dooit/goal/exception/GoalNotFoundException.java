package pj.dooit.goal.exception;

import lombok.Getter;

@Getter
public class GoalNotFoundException extends RuntimeException {

    private final String detail;

    public GoalNotFoundException(String detail) {
        super(detail);
        this.detail = detail;
    }
}
