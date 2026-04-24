package com.smartticket.agent.tool.parameter;

/**
 * Agent Tool 可校验的参数字段。
 */
public enum AgentToolParameterField {
    TICKET_ID("工单 ID"),
    ASSIGNEE_ID("目标处理人 ID"),
    TITLE("工单标题"),
    DESCRIPTION("问题描述"),
    CATEGORY("工单分类"),
    PRIORITY("工单优先级");

    // label
    private final String label;

    AgentToolParameterField(String label) {
        this.label = label;
    }

    /**
     * 获取Label。
     */
    public String getLabel() {
        return label;
    }
}
