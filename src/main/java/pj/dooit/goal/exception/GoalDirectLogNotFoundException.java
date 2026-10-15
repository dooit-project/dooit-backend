package pj.dooit.goal.exception;

import lombok.Getter;

@Getter
public class GoalDirectLogNotFoundException extends RuntimeException {

    private final String detail;

    public GoalDirectLogNotFoundException(String detail) {
        super(detail);
        this.detail = detail;
    }
}
