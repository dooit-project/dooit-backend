package pj.dooit.task.exception;

import pj.dooit.common.api.ErrorCode;
import lombok.Getter;

@Getter
public class TaskValidationException extends RuntimeException {

    private final ErrorCode errorCode = ErrorCode.INVALID_INPUT;
    private final String detail;

    public TaskValidationException(String detail) {
        super(ErrorCode.INVALID_INPUT.getMessage());
        this.detail = detail;
    }
}
