package pj.dooit.goal.exception;

import lombok.Getter;

@Getter
public class GoalTaskConflictException extends RuntimeException {

    private final String detail;

    public GoalTaskConflictException(String detail) {
        super(detail);
        this.detail = detail;
    }
}
