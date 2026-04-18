package com.smartticket.common.exception;

/**
 * Centralized business error codes and default messages.
 */
public enum BusinessErrorCode {
    INVALID_TICKET_STATUS("INVALID_TICKET_STATUS", "工单状态不合法: %s"),
    INVALID_TICKET_CATEGORY("INVALID_TICKET_CATEGORY", "不支持的工单分类: %s"),
    INVALID_TICKET_PRIORITY("INVALID_TICKET_PRIORITY", "不支持的工单优先级: %s"),
    TICKET_FORBIDDEN("TICKET_FORBIDDEN", "当前用户无权查看该工单"),
    ADMIN_REQUIRED("ADMIN_REQUIRED", "该操作需要 ADMIN 角色"),
    TICKET_TRANSFER_FORBIDDEN("TICKET_TRANSFER_FORBIDDEN", "只有当前负责人或管理员可以转派工单"),
    TICKET_RESOLVE_FORBIDDEN("TICKET_RESOLVE_FORBIDDEN", "只有当前负责人或管理员可以解决工单"),
    TICKET_CLOSE_FORBIDDEN("TICKET_CLOSE_FORBIDDEN", "只有提单人或管理员可以关闭工单"),
    TICKET_STATUS_REQUIRED("TICKET_STATUS_REQUIRED", "目标状态不能为空"),
    TICKET_CLOSED("TICKET_CLOSED", "已关闭工单不能继续评论"),
    TICKET_STATUS_UNCHANGED("TICKET_STATUS_UNCHANGED", "工单已经处于目标状态"),
    TICKET_ASSIGNEE_REQUIRED("TICKET_ASSIGNEE_REQUIRED", "进入处理中前必须先分配处理人"),
    INVALID_TICKET_STATUS_TRANSITION(
            "INVALID_TICKET_STATUS_TRANSITION",
            "状态流转不合法，只允许 PENDING_ASSIGN -> PROCESSING -> RESOLVED -> CLOSED"
    ),
    TICKET_NOT_FOUND("TICKET_NOT_FOUND", "工单不存在"),
    ASSIGNEE_NOT_FOUND("ASSIGNEE_NOT_FOUND", "目标处理人不存在或已禁用"),
    ASSIGNEE_NOT_STAFF("ASSIGNEE_NOT_STAFF", "目标处理人必须具备 STAFF 角色");

    private final String code;
    private final String message;

    BusinessErrorCode(String code, String message) {
        this.code = code;
        this.message = message;
    }

    public String getCode() {
        return code;
    }

    public String getMessage() {
        return message;
    }

    public String formatMessage(Object... args) {
        if (args == null || args.length == 0) {
            return message;
        }
        return String.format(message, args);
    }
}
