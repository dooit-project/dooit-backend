package pj.dooit.task.exception;

import pj.dooit.common.api.ErrorCode;
import lombok.Getter;

@Getter
public class TaskNotFoundException extends RuntimeException {

    private final ErrorCode errorCode = ErrorCode.TASK_NOT_FOUND;
    private final String detail;

    public TaskNotFoundException(long id) {
        super(ErrorCode.TASK_NOT_FOUND.getMessage());
        this.detail = "Task not found. id = " + id;
    }
}
