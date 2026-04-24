package com.smartticket.agent.planner;

/**
 * 智能体计划阶段枚举定义。
 */
public enum AgentPlanStage {
    ROUTE_INTENT,
    COLLECT_REQUIRED_SLOTS,
    EXECUTE_SKILL,
    SUMMARIZE_RESULT,
    WAIT_USER,
    /** LLM 驱动的多步推理执行阶段（ReAct 循环）。 */
    AGENT_THINKING
}
