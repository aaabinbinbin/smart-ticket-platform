package com.smartticket.agent.planner;

/**
 * 智能体计划阶段枚举定义。
 */
public enum AgentPlanStage {
    ROUTE_INTENT,
    COLLECT_REQUIRED_SLOTS,
    CHECK_CONTEXT,
    EXECUTE_SKILL,
    SUMMARIZE_RESULT,
    WAIT_USER
}
