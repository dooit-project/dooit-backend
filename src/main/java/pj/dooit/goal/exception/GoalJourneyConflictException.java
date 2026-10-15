package pj.dooit.goal.exception;

import lombok.Getter;

@Getter
public class GoalJourneyConflictException extends RuntimeException {

    private final String detail;

    public GoalJourneyConflictException(String detail) {
        super(detail);
        this.detail = detail;
    }
}
