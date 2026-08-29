package pj.dooit.task.exception;

import lombok.Getter;

@Getter
public class TaskOrderConflictException extends RuntimeException {

    private final String detail;

    public TaskOrderConflictException(String detail) {
        super(detail);
        this.detail = detail;
    }
}
