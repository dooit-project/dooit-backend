package pj.dooit.goal.exception;

import lombok.Getter;

@Getter
public class GoalOccurrenceNotFoundException extends RuntimeException {

    private final String detail;

    public GoalOccurrenceNotFoundException(String detail) {
        super(detail);
        this.detail = detail;
    }
}
