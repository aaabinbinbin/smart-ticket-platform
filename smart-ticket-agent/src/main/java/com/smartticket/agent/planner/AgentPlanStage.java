package com.smartticket.agent.planner;

public enum AgentPlanStage {
    ROUTE_INTENT,
    COLLECT_REQUIRED_SLOTS,
    CHECK_CONTEXT,
    EXECUTE_SKILL,
    SUMMARIZE_RESULT,
    WAIT_USER
}
