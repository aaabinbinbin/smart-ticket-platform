package com.smartticket.agent.llm.prompt;

/**
 * Agent Prompt 模板名称。
 *
 * <p>枚举值用于代码内部识别，templateName 用于文档、日志或后续资源文件命名。</p>
 */
public enum AgentPromptName {
    /**
     * 意图识别 Prompt。
     */
    INTENT_CLASSIFICATION("intent-classification"),

    /**
     * 工单参数抽取 Prompt。
     */
    TICKET_PARAMETER_EXTRACTION("ticket-parameter-extraction"),

    /**
     * 缺参澄清问题生成 Prompt。
     */
    CLARIFICATION_QUESTION("clarification-question"),

    /**
     * 工具执行结果总结 Prompt。
     */
    RESPONSE_SUMMARY("response-summary");

    /**
     * 模板外部名称，保持与文档中的模板名称一致。
     */
    private final String templateName;

    AgentPromptName(String templateName) {
        this.templateName = templateName;
    }

    /**
     * 获取模板外部名称。
     */
    public String getTemplateName() {
        return templateName;
    }
}
