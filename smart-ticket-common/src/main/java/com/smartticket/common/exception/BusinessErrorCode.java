package com.smartticket.common.exception;

/**
 * 统一业务错误码和默认错误消息。
 *
 * <p>业务代码抛出异常时应优先使用这里的枚举，避免在各处散落硬编码错误字符串。</p>
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
    TICKET_STATE_CHANGED("TICKET_STATE_CHANGED", "工单状态已变化，请刷新后重试"),
    CLOSE_TICKET_USE_CLOSE_API("CLOSE_TICKET_USE_CLOSE_API", "关闭工单请使用关闭接口"),
    INVALID_TICKET_STATUS_TRANSITION(
            "INVALID_TICKET_STATUS_TRANSITION",
            "状态流转不合法，只允许 PENDING_ASSIGN -> PROCESSING -> RESOLVED -> CLOSED"
    ),
    TICKET_NOT_FOUND("TICKET_NOT_FOUND", "工单不存在"),
    ASSIGNEE_NOT_FOUND("ASSIGNEE_NOT_FOUND", "目标处理人不存在或已禁用"),
    ASSIGNEE_NOT_STAFF("ASSIGNEE_NOT_STAFF", "目标处理人必须具备 STAFF 角色"),
    TICKET_GROUP_NOT_FOUND("TICKET_GROUP_NOT_FOUND", "工单组不存在"),
    TICKET_GROUP_CODE_DUPLICATED("TICKET_GROUP_CODE_DUPLICATED", "工单组编码已存在: %s"),
    TICKET_GROUP_DISABLED("TICKET_GROUP_DISABLED", "工单组已停用"),
    TICKET_QUEUE_NOT_FOUND("TICKET_QUEUE_NOT_FOUND", "工单队列不存在"),
    TICKET_QUEUE_CODE_DUPLICATED("TICKET_QUEUE_CODE_DUPLICATED", "工单队列编码已存在: %s"),
    TICKET_SLA_POLICY_NOT_FOUND("TICKET_SLA_POLICY_NOT_FOUND", "SLA 策略不存在"),
    TICKET_SLA_INSTANCE_NOT_FOUND("TICKET_SLA_INSTANCE_NOT_FOUND", "工单 SLA 实例不存在"),
    INVALID_TICKET_SLA_POLICY("INVALID_TICKET_SLA_POLICY", "SLA 策略配置不合法: %s"),
    TICKET_ASSIGNMENT_RULE_NOT_FOUND("TICKET_ASSIGNMENT_RULE_NOT_FOUND", "自动分派规则不存在"),
    TICKET_ASSIGNMENT_RULE_NOT_MATCHED("TICKET_ASSIGNMENT_RULE_NOT_MATCHED", "未匹配到可用的自动分派规则"),
    INVALID_TICKET_ASSIGNMENT_RULE("INVALID_TICKET_ASSIGNMENT_RULE", "自动分派规则配置不合法: %s"),
    INVALID_IDEMPOTENCY_KEY("INVALID_IDEMPOTENCY_KEY", "幂等键不合法，长度不能超过 128 个字符且不能包含控制字符"),
    IDEMPOTENT_REQUEST_PROCESSING("IDEMPOTENT_REQUEST_PROCESSING", "相同幂等键的创建请求正在处理中，请稍后重试");

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
