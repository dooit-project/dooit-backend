package com.todolab.workspace.exception;

import com.todolab.common.api.ErrorCode;
import lombok.Getter;

@Getter
public class WorkspaceNotFoundException extends RuntimeException {

    private final ErrorCode errorCode = ErrorCode.WORKSPACE_NOT_FOUND;
    private final String detail;

    public WorkspaceNotFoundException(long id) {
        super(ErrorCode.WORKSPACE_NOT_FOUND.getMessage());
        this.detail = "Workspace not found. id = " + id;
    }
}
