package com.todolab.workspace.exception;

import com.todolab.common.api.ErrorCode;
import lombok.Getter;

@Getter
public class WorkspaceMemberNotFoundException extends RuntimeException {

    private final ErrorCode errorCode = ErrorCode.WORKSPACE_MEMBER_NOT_FOUND;
    private final String detail;

    public WorkspaceMemberNotFoundException(long id) {
        super(ErrorCode.WORKSPACE_MEMBER_NOT_FOUND.getMessage());
        this.detail = "Workspace member not found. id = " + id;
    }
}
