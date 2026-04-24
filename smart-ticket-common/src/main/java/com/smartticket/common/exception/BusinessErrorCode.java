package com.smartticket.common.exception;

/**
 * BusinessError编码枚举定义。
 */
public enum BusinessErrorCode {
    UNAUTHORIZED("UNAUTHORIZED", "未登录或提供的凭证无效"),
    INVALID_TICKET_STATUS("INVALID_TICKET_STATUS", "不支持的工单状态: %s"),
    INVALID_TICKET_TYPE("INVALID_TICKET_TYPE", "不支持的工单类型: %s"),
    INVALID_TICKET_SUMMARY_VIEW("INVALID_TICKET_SUMMARY_VIEW", "不支持的工单摘要视角: %s"),
    INVALID_TICKET_TYPE_REQUIREMENT("INVALID_TICKET_TYPE_REQUIREMENT", "工单类型要求校验未通过: %s"),
    INVALID_TICKET_CATEGORY("INVALID_TICKET_CATEGORY", "不支持的工单分类: %s"),
    INVALID_TICKET_PRIORITY("INVALID_TICKET_PRIORITY", "不支持的工单优先级: %s"),
    TICKET_FORBIDDEN("TICKET_FORBIDDEN", "当前用户无权查看该工单"),
    ADMIN_REQUIRED("ADMIN_REQUIRED", "当前操作需要 ADMIN 角色"),
    TICKET_TRANSFER_FORBIDDEN("TICKET_TRANSFER_FORBIDDEN", "只有当前处理人或管理员可以转派工单"),
    TICKET_RESOLVE_FORBIDDEN("TICKET_RESOLVE_FORBIDDEN", "只有当前处理人或管理员可以解决工单"),
    TICKET_CLOSE_FORBIDDEN("TICKET_CLOSE_FORBIDDEN", "只有提单人或管理员可以关闭工单"),
    TICKET_CLAIM_FORBIDDEN("TICKET_CLAIM_FORBIDDEN", "当前用户无权认领该工单"),
    TICKET_APPROVAL_FORBIDDEN("TICKET_APPROVAL_FORBIDDEN", "当前用户无权审批该工单: %s"),
    INVALID_TICKET_CLAIM("INVALID_TICKET_CLAIM", "工单认领参数不合法: %s"),
    INVALID_TICKET_APPROVAL("INVALID_TICKET_APPROVAL", "工单审批参数不合法: %s"),
    TICKET_APPROVAL_REQUIRED("TICKET_APPROVAL_REQUIRED", "当前操作需要审批通过: %s"),
    TICKET_STATUS_REQUIRED("TICKET_STATUS_REQUIRED", "目标状态不能为空"),
    TICKET_CLOSED("TICKET_CLOSED", "工单已关闭，不能继续操作"),
    TICKET_STATUS_UNCHANGED("TICKET_STATUS_UNCHANGED", "新状态不能与当前状态相同"),
    TICKET_ASSIGNEE_REQUIRED("TICKET_ASSIGNEE_REQUIRED", "进入处理中前必须先指定处理人"),
    TICKET_STATE_CHANGED("TICKET_STATE_CHANGED", "工单状态已变化，请刷新后重试"),
    CLOSE_TICKET_USE_CLOSE_API("CLOSE_TICKET_USE_CLOSE_API", "关闭工单必须使用关闭接口"),
    INVALID_TICKET_STATUS_TRANSITION(
            "INVALID_TICKET_STATUS_TRANSITION",
            "状态流转不合法，当前只支持 PENDING_ASSIGN -> PROCESSING -> RESOLVED -> CLOSED"
    ),
    TICKET_NOT_FOUND("TICKET_NOT_FOUND", "工单不存在"),
    TICKET_APPROVAL_NOT_FOUND("TICKET_APPROVAL_NOT_FOUND", "工单审批记录不存在"),
    TICKET_APPROVAL_TEMPLATE_NOT_FOUND("TICKET_APPROVAL_TEMPLATE_NOT_FOUND", "审批模板不存在"),
    ASSIGNEE_NOT_FOUND("ASSIGNEE_NOT_FOUND", "目标处理人不存在"),
    ASSIGNEE_NOT_STAFF("ASSIGNEE_NOT_STAFF", "目标处理人不是 STAFF 角色"),
    TICKET_GROUP_NOT_FOUND("TICKET_GROUP_NOT_FOUND", "工单组不存在"),
    TICKET_GROUP_CODE_DUPLICATED("TICKET_GROUP_CODE_DUPLICATED", "工单组编码已存在: %s"),
    TICKET_GROUP_DISABLED("TICKET_GROUP_DISABLED", "工单组已停用"),
    TICKET_QUEUE_NOT_FOUND("TICKET_QUEUE_NOT_FOUND", "工单队列不存在"),
    TICKET_QUEUE_CODE_DUPLICATED("TICKET_QUEUE_CODE_DUPLICATED", "工单队列编码已存在: %s"),
    TICKET_SLA_POLICY_NOT_FOUND("TICKET_SLA_POLICY_NOT_FOUND", "SLA 策略不存在"),
    TICKET_SLA_INSTANCE_NOT_FOUND("TICKET_SLA_INSTANCE_NOT_FOUND", "工单 SLA 实例不存在"),
    TICKET_NOTIFICATION_NOT_FOUND("TICKET_NOTIFICATION_NOT_FOUND", "站内通知不存在"),
    INVALID_TICKET_SLA_POLICY("INVALID_TICKET_SLA_POLICY", "SLA 策略配置不合法: %s"),
    TICKET_ASSIGNMENT_RULE_NOT_FOUND("TICKET_ASSIGNMENT_RULE_NOT_FOUND", "自动分派规则不存在"),
    TICKET_ASSIGNMENT_RULE_NOT_MATCHED("TICKET_ASSIGNMENT_RULE_NOT_MATCHED", "未匹配到可用的自动分派规则"),
    INVALID_TICKET_ASSIGNMENT_RULE("INVALID_TICKET_ASSIGNMENT_RULE", "自动分派规则参数不合法: %s"),
    INVALID_IDEMPOTENCY_KEY("INVALID_IDEMPOTENCY_KEY", "幂等键不合法，必须是长度不超过 128 的非空字符串"),
    IDEMPOTENT_REQUEST_PROCESSING("IDEMPOTENT_REQUEST_PROCESSING", "相同幂等键的请求正在处理中，请稍后重试");

    // 编码
    private final String code;
    // 消息
    private final String message;

    BusinessErrorCode(String code, String message) {
        this.code = code;
        this.message = message;
    }

    /**
     * 获取编码。
     */
    public String getCode() {
        return code;
    }

    /**
     * 获取消息。
     */
    public String getMessage() {
        return message;
    }

    /**
     * 处理消息。
     */
    public String formatMessage(Object... args) {
        if (args == null || args.length == 0) {
            return message;
        }
        return String.format(message, args);
    }
}
