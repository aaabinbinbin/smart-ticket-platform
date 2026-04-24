package com.smartticket.agent.planner;

/**
 * 智能体计划动作枚举定义。
 */
public enum AgentPlanAction {
    CLARIFY_INTENT,
    COLLECT_SLOTS,
    EXECUTE_TOOL,
    CONFIRM_HIGH_RISK,
    RETURN_RESULT,
    /** LLM 主动调用多个工具完成复杂任务。 */
    REACT_REASONING
}
