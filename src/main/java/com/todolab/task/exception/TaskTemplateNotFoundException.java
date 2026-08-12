package com.todolab.task.exception;

import com.todolab.common.api.ErrorCode;
import lombok.Getter;

@Getter
public class TaskTemplateNotFoundException extends RuntimeException {

    private final ErrorCode errorCode = ErrorCode.TASK_TEMPLATE_NOT_FOUND;
    private final String detail;

    public TaskTemplateNotFoundException(long id) {
        super(ErrorCode.TASK_TEMPLATE_NOT_FOUND.getMessage());
        this.detail = "Task template not found. id = " + id;
    }
}
