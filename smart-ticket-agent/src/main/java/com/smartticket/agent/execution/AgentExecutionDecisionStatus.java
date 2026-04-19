package com.smartticket.agent.execution;

/**
 * Agent 执行前置决策状态。
 *
 * <p>该状态用于表达 Tool 调用计划在真正执行前的处理结果，让编排层不用关心每一种校验细节。</p>
 */
public enum AgentExecutionDecisionStatus {
    /** 已通过所有执行前校验，可以调用 Tool。 */
    ALLOW_EXECUTE,

    /** 高风险写操作还缺少用户明确确认。 */
    NEED_CONFIRMATION,

    /** Tool 必填参数不完整，需要继续追问用户。 */
    NEED_MORE_INFO,

    /** 计划本身不合法，不能进入执行阶段。 */
    REJECTED
}
