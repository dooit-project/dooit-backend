package pj.dooit.goal.exception;

import lombok.Getter;

@Getter
public class GoalOccurrenceConflictException extends RuntimeException {

    private final String detail;

    public GoalOccurrenceConflictException(String detail) {
        super(detail);
        this.detail = detail;
    }
}
