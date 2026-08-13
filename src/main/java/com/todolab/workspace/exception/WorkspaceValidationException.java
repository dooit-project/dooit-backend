package com.todolab.workspace.exception;

import lombok.Getter;

@Getter
public class WorkspaceValidationException extends RuntimeException {

    private final String detail;

    public WorkspaceValidationException(String detail) {
        super(detail);
        this.detail = detail;
    }
}
