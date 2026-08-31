package pj.dooit.task.exception;

import pj.dooit.common.api.ErrorCode;
import lombok.Getter;

@Getter
public class TaskChecklistItemNotFoundException extends RuntimeException {

    private final ErrorCode errorCode = ErrorCode.TASK_CHECKLIST_ITEM_NOT_FOUND;
    private final String detail;

    public TaskChecklistItemNotFoundException(long id) {
        super(ErrorCode.TASK_CHECKLIST_ITEM_NOT_FOUND.getMessage());
        this.detail = "Task checklist item not found. id = " + id;
    }
}
